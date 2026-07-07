package tech.kzen.auto.server.exec.report

import com.lmax.disruptor.ExceptionHandler
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputType
import tech.kzen.auto.plugin.api.managed.PipelineOutput
import tech.kzen.auto.plugin.definition.ReportDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.auto.server.objects.report.exec.ReportInputPipeline
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import tech.kzen.auto.server.objects.report.exec.event.output.DisruptorPipelineOutput
import tech.kzen.auto.server.objects.report.exec.input.connect.file.FileFlatDataSource
import tech.kzen.auto.server.objects.report.exec.input.model.data.DatasetDefinition
import tech.kzen.auto.server.objects.report.exec.input.model.data.DatasetInfo
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataContentDefinition
import tech.kzen.auto.server.objects.report.exec.input.model.instance.ReportDataInstance
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportInputReader
import tech.kzen.auto.server.objects.report.exec.output.TableReportOutput
import tech.kzen.auto.server.objects.report.exec.output.export.CharsetExportEncoder
import tech.kzen.auto.server.objects.report.exec.output.export.CompressedExportWriter
import tech.kzen.auto.server.objects.report.exec.output.export.ExportColumnNormalizer
import tech.kzen.auto.server.objects.report.exec.output.export.format.ExportFormatter
import tech.kzen.auto.server.objects.report.exec.output.export.model.ExportFormat
import tech.kzen.auto.server.objects.report.exec.stages.*
import tech.kzen.auto.server.objects.report.exec.summary.ReportSummary
import tech.kzen.auto.server.objects.report.exec.trace.ReportInputTrace
import tech.kzen.auto.server.objects.report.exec.trace.ReportOutputTrace
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.objects.report.service.ReportWorkPool
import tech.kzen.auto.server.service.plugin.ReportDefinitionRepository
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.auto.server.util.DisruptorUtils
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean


/**
 * One run of a Report document on the new engine — the coroutine-shaped successor to
 * [tech.kzen.auto.server.objects.report.ReportExecution]'s re-entrant `init` + `continueOrStart` + `close`.
 * The whole disruptor record pipeline (formula → filter → summary / table-output / export) is reused verbatim;
 * only the four logic-framework seams are swapped onto [Execution]:
 *
 * - **Cancellation / pause** — the input poll loop calls [Execution.checkpoint] each iteration (instead of the
 *   old `LogicControl.pollCommand() == Cancel`). It suspends while the run is paused and throws
 *   [CancellationException] on cancel; the run then settles Cancelled (status persisted to the run dir).
 * - **Result** — returns [TupleValue.empty] on success (run dir → Done) and throws on failure (→ Failed),
 *   rather than returning a `LogicResult`.
 * - **Trace** — input / output progress is written through an [ExecutionLogicTraceHandle] (literal trace paths
 *   bridged via [Execution.emit]) rather than a [tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle] handed
 *   in by the old framework.
 * - **Duplex requests** — the online output / summary preview handler is registered via [Execution.onRequest]
 *   (was `LogicControl.subscribeRequest`).
 *
 * The run dir is prepared here at the top of [run] (was [tech.kzen.auto.server.objects.report.ReportDocument]'s
 * old `execute`), stamped with the controller's [runExecutionId] so the offline progress lookup correlates the
 * persisted output with the (retained) trace buffer.
 *
 * DEFERRED (tracked parity gap, mirroring the Flow / Job first ports): live-edit migration. [ReportLogic]
 * registers no [Execution.onCapture], so an edit while running cleanly restarts the report on the edited
 * definition (the safe best-effort §5 default).
 */
class ReportRun(
    private val execution: Execution,
    private val reportRunContext: ReportRunContext,
    private val reportWorkPool: ReportWorkPool,
    private val runExecutionId: LogicRunExecutionId,
    private val definitionRepository: ReportDefinitionRepository,
    private val calculatedColumnEval: CalculatedColumnEval
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportRun::class.java)

        private const val preCachePartitionCount = 3
        private const val recordDisruptorBufferSize = 32 * 1024

        // MULTI, not SINGLE: the record ring is published from two threads — the input pipeline's last
        // model-stage thread (records) and the run coroutine (the end-of-data sentinel in
        // waitForProcessingToFinish). awaitEndOfData sequences them (never concurrent), so SINGLE would
        // appear to work, but LMAX's SingleProducerSequencer asserts on producer-thread identity
        // (assertions are on under test).
        private val recordProducerType = ProducerType.MULTI


        fun outputInfoOffline(
            reportRunContext: ReportRunContext,
            reportWorkPool: ReportWorkPool
        ): OutputInfo {
            val isMissing = !Files.exists(reportRunContext.runDir)
            if (isMissing) {
                return OutputInfo(
                    reportRunContext.runDir.toString(),
                    null,
                    null,
                    OutputStatus.Missing,
                    null)
            }

            val status = reportWorkPool.readRunStatus(reportRunContext.runDir)
            val runExecutionId = reportWorkPool.readRunExecutionId(reportRunContext.runDir)

            val withoutPreview = OutputInfo(
                reportRunContext.runDir.toString(),
                null,
                null,
                status,
                runExecutionId)

            return if (reportRunContext.output.type == OutputType.Explore) {
                val outputTableInfo = TableReportOutput.outputInfoOffline(
                    reportRunContext, reportRunContext.output.explore)

                if (outputTableInfo == null) {
                    withoutPreview.copy(status = OutputStatus.Failed)
                }
                else {
                    withoutPreview.copy(table = outputTableInfo)
                }
            }
            else {
                withoutPreview
            }
        }


        private data class RecordDisruptor(
            val disruptor: Disruptor<ReportOutputEvent<Any>>
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val trace = ExecutionLogicTraceHandle(execution)

    private val failed = AtomicBoolean(false)

    @Volatile
    private var cancelled = false

    private val preCachePartitions = PipelinePreCacheStage.partitions(preCachePartitionCount)

    private val summary = PipelineSummaryStage(
        ReportSummary(reportRunContext, reportRunContext.runDir))

    private var tableOutput: PipelineOutputTableStage? = null
    private var exportWriter: CompressedExportWriter? = null


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun run(): TupleValue {
        prepareRunDir()

        if (reportRunContext.output.type == OutputType.Explore) {
            tableOutput = PipelineOutputTableStage(
                TableReportOutput(reportRunContext, ReportOutputTrace(trace)))
        }
        else {
            exportWriter = CompressedExportWriter(reportRunContext.output.export)
        }

        execution.onRequest(::pollRequest)

        var error = false
        try {
            runPipeline()

            return when {
                failed.get() -> {
                    error = true
                    reportWorkPool.updateRunStatus(reportRunContext.runDir, OutputStatus.Failed)
                    throw IllegalStateException("Report run failed")
                }

                else -> {
                    reportWorkPool.updateRunStatus(reportRunContext.runDir, OutputStatus.Done)
                    TupleValue.empty
                }
            }
        }
        catch (e: CancellationException) {
            reportWorkPool.updateRunStatus(reportRunContext.runDir, OutputStatus.Cancelled)
            throw e
        }
        catch (e: Throwable) {
            error = true
            reportWorkPool.updateRunStatus(reportRunContext.runDir, OutputStatus.Failed)
            throw e
        }
        finally {
            close(error)
        }
    }


    private fun prepareRunDir() {
        val runDir = reportRunContext.runDir
        val created = reportWorkPool.prepareRunDir(runDir, runExecutionId)
        if (!created) {
            WorkUtils.recursivelyDeleteDir(runDir)
            val createdRetry = reportWorkPool.prepareRunDir(runDir, runExecutionId)
            check(createdRetry) { "Unable to re-create: $runDir" }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runPipeline() {
        val datasetInfo = reportRunContext.datasetInfo

        datasetDefinition<Any>(datasetInfo).use { datasetDefinition ->
            val recordDisruptor = setupRecordDisruptor(datasetDefinition.classLoaderHandle)
            recordDisruptor.disruptor.start()

            val recordDisruptorInput = DisruptorPipelineOutput(recordDisruptor.disruptor.ringBuffer)
            try {
                for (flatDataContentDefinition in datasetDefinition.items) {
                    runFlatData(recordDisruptorInput, flatDataContentDefinition)

                    if (failed.get() || cancelled) {
                        break
                    }
                }
            }
            finally {
                waitForProcessingToFinish(recordDisruptorInput)
                recordDisruptor.disruptor.shutdown()
            }
        }
    }


    private fun <T> datasetDefinition(datasetInfo: DatasetInfo): DatasetDefinition<T> {
        val pluginCoordinates = datasetInfo.items.map { it.processorPluginCoordinate }.toSet()
        val classLoaderHandle = definitionRepository
            .classLoaderHandle(pluginCoordinates, ClassLoaderUtils.dynamicParentClassLoader())

        val cache = mutableMapOf<PluginCoordinate, ReportDefinition<T>>()
        val builder = mutableListOf<FlatDataContentDefinition<T>>()

        for (flatDataInfo in datasetInfo.items) {
            val processorPluginCoordinate = flatDataInfo.processorPluginCoordinate

            val processorDataDefinition =
                cache.getOrPut(processorPluginCoordinate) {
                    processorDataDefinition(processorPluginCoordinate, classLoaderHandle)
                }

            builder.add(
                FlatDataContentDefinition(
                    flatDataInfo,
                    FileFlatDataSource.instance,
                    processorDataDefinition))
        }

        builder.sortBy { it.flatDataInfo }

        return DatasetDefinition(builder, classLoaderHandle)
    }


    private fun <T> processorDataDefinition(
        processorDefinitionCoordinate: PluginCoordinate,
        classLoaderHandle: ClassLoaderHandle
    ): ReportDefinition<T> {
        val definition = definitionRepository.define(
            processorDefinitionCoordinate, classLoaderHandle)

        @Suppress("UNCHECKED_CAST")
        return definition as ReportDefinition<T>
    }


    private suspend fun <T> runFlatData(
        recordDisruptorInput: PipelineOutput<ReportOutputEvent<T>>,
        flatDataContentDefinition: FlatDataContentDefinition<T>
    ) {
        val flatDataStream = flatDataContentDefinition.open()
        val totalSize = flatDataContentDefinition.size()

        val flatDataLocation = flatDataContentDefinition.flatDataInfo.flatDataLocation
        val reportInputTrace = ReportInputTrace(trace, flatDataLocation.dataLocation, totalSize)

        val reportInputReader = ReportInputReader(flatDataStream, reportInputTrace)

        val reportDataInstance = ReportDataInstance(
            flatDataContentDefinition.reportDefinition.reportDataDefinition)

        val reportInputPipeline = ReportInputPipeline(
            reportInputReader,
            recordDisruptorInput,
            reportDataInstance,
            flatDataLocation.dataEncoding,
            flatDataContentDefinition.flatDataInfo,
            reportInputTrace,
            failed)

        var reachedEndOfData = false
        reportInputPipeline.start()
        try {
            reportInputTrace.startReading()

            while (!failed.get()) {
                // Settle at a boundary: suspends while paused, throws CancellationException on cancel. Setting
                // `cancelled` before rethrowing lets the finally below mark this file's parse as
                // not-cleanly-finished.
                try {
                    execution.checkpoint()
                }
                catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }

                val hasNext = reportInputPipeline.poll()
                if (!hasNext) {
                    reachedEndOfData = true
                    break
                }
            }
        }
        finally {
            reportInputPipeline.close(reachedEndOfData)

            waitForProcessingToFinish(recordDisruptorInput)

            val reachedEndWithoutFailOrCancel = !failed.get() && !cancelled
            reportInputTrace.finishParsing(reachedEndWithoutFailOrCancel)
        }
    }


    private fun <T> waitForProcessingToFinish(
        recordDisruptorInput: PipelineOutput<ReportOutputEvent<T>>
    ) {
        val sentinelEvent = recordDisruptorInput.next()
        val sentinel = sentinelEvent.setSentinel()

        recordDisruptorInput.commit()

        sentinel.await()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun pollRequest(executionRequest: ExecutionRequest): ExecutionResult {
        val action = executionRequest.getSingle(ReportConventions.paramAction)
            ?: return ExecutionResult.failure("Missing action")

        return when (action) {
            ReportConventions.actionOutputInfoOnline ->
                pollOutputInfoRequest(executionRequest)

            ReportConventions.actionSummaryOnline ->
                pollSummaryRequest()

            else ->
                ExecutionResult.failure("Unknown action: $action")
        }
    }


    private fun pollOutputInfoRequest(executionRequest: ExecutionRequest): ExecutionResult {
        val withoutPreview = OutputInfo(
            reportRunContext.runDir.toString(),
            null,
            null,
            OutputStatus.Running,
            runExecutionId)

        val pivotValueTableSpec = PivotValueTableSpec.ofRequest(executionRequest.parameters)
        val start = executionRequest.getLong(OutputExploreSpec.previewStartKey)!!
        val count = executionRequest.getInt(OutputExploreSpec.previewRowCountKey)!!

        val withPreview =
            if (tableOutput != null) {
                val outputTableInfo = tableOutput!!.preview(pivotValueTableSpec, start, count)

                if (outputTableInfo == null) {
                    withoutPreview.copy(status = OutputStatus.Failed)
                }
                else {
                    withoutPreview.copy(table = outputTableInfo)
                }
            }
            else {
                withoutPreview
            }

        return ExecutionResult.success(ExecutionValue.of(
            withPreview.toCollection()))
    }


    private fun pollSummaryRequest(): ExecutionResult {
        val response = summary.reportSummary.previewFromOtherThread()
            ?: return ExecutionResult.failure("Summary failed")

        return ExecutionResult.success(ExecutionValue.of(
            response.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun setupRecordDisruptor(
        classLoaderHandle: ClassLoaderHandle
    ): RecordDisruptor {
        val recordDisruptor = Disruptor(
            { ReportOutputEvent<Any>() },
            recordDisruptorBufferSize,
            DaemonThreadFactory.INSTANCE,
            recordProducerType,
            DisruptorUtils.newWaitStrategy()
        )

        val formulas = ReportFormulaStage(
            reportRunContext.dataType,
            reportRunContext.formula,
            classLoaderHandle.classLoader,
            calculatedColumnEval)

        var builder = recordDisruptor.handleEventsWith(formulas)

        val filter = ReportFilterStage(reportRunContext)
        if (!filter.isEmpty()) {
            builder = builder.then(filter)
        }

        if (reportRunContext.previewAll.enabled) {
            TODO("Preview All not implemented (yet)")
        }
        val previewEnabled = reportRunContext.previewFiltered.enabled

        val startOfOutput =
            if (tableOutput != null) {
                tableOutput
            }
            else {
                ExportColumnNormalizer(
                    reportRunContext.analysisColumnInfo.filteredInputAndCalculatedColumns)
            }

        builder =
            if (previewEnabled) {
                builder
                    .then(*preCachePartitions)
                    .then(summary, startOfOutput)
            }
            else {
                builder
                    .then(startOfOutput)
            }

        if (exportWriter != null) {
            builder
                .then(ExportFormatter(
                    ExportFormat.byName(reportRunContext.output.export.format),
                    reportRunContext.analysisColumnInfo.filteredInputAndCalculatedColumns,
                    reportRunContext.reportDocumentName,
                    reportRunContext.output.export
                ))
                .then(CharsetExportEncoder(Charsets.UTF_8))
                .then(exportWriter)
        }

        recordDisruptor.setDefaultExceptionHandler(recordExceptionHandler())

        return RecordDisruptor(recordDisruptor)
    }


    private fun recordExceptionHandler(): ExceptionHandler<ReportOutputEvent<*>> {
        return object : ExceptionHandler<ReportOutputEvent<*>> {
            override fun handleEventException(ex: Throwable, sequence: Long, event: ReportOutputEvent<*>) {
                if (failed.get()) {
                    return
                }
                logger.error("Record event - {}", event.row, ex)
                failed.set(true)
            }

            override fun handleOnStartException(ex: Throwable) {
                if (failed.get()) {
                    return
                }
                logger.error("Record start", ex)
                failed.set(true)
            }

            override fun handleOnShutdownException(ex: Throwable) {
                if (failed.get()) {
                    return
                }
                logger.error("Record shutdown", ex)
                failed.set(true)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun close(error: Boolean) {
        summary.close()
        tableOutput?.close(error)
        exportWriter?.close()

        when {
            error -> logger.warn("Report run failed")
            cancelled -> logger.info("Report run cancelled")
            else -> logger.info("Report run completed successfully")
        }
    }
}
