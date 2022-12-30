package tech.kzen.auto.server.objects.report

import com.lmax.disruptor.ExceptionHandler
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputType
import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.plugin.api.managed.PipelineOutput
import tech.kzen.auto.plugin.definition.ReportDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.auto.server.objects.report.exec.ReportInputPipeline
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
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.auto.server.util.DisruptorUtils
import tech.kzen.lib.common.model.definition.GraphDefinition
import java.nio.file.Files
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class ReportExecution(
    private val initialReportRunContext: ReportRunContext,
    private val reportWorkPool: ReportWorkPool,
    private val trace: LogicTraceHandle,
    private val runExecutionId: LogicRunExecutionId
):
    LogicExecution
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportExecution::class.java)


//        private const val preCachePartitionCount = 0
//        private const val preCachePartitionCount = 1
//        private const val preCachePartitionCount = 2
        private const val preCachePartitionCount = 3
//        private const val preCachePartitionCount = 4

//        private const val recordDisruptorBufferSize = 16 * 1024
        private const val recordDisruptorBufferSize = 32 * 1024
//        private const val recordDisruptorBufferSize = 64 * 1024


        // NB: multiple input threads plus sentinel
        private val recordProducerType =
            ProducerType.SINGLE
//            ProducerType.MULTI


        fun outputInfoOffline(
            reportRunContext: ReportRunContext,
            reportWorkPool: ReportWorkPool
        ): OutputInfo {
            val isMissing = ! Files.exists(reportRunContext.runDir)
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

            val withPreview =
                if (reportRunContext.output.type == OutputType.Explore) {
                    val outputTableInfo = TableReportOutput.outputInfoOffline(
                        reportRunContext, reportRunContext.output.explore)

                    if (outputTableInfo == null) {
                        withoutPreview.copy(
                            status = OutputStatus.Failed)
                    }
                    else {
                        withoutPreview.copy(
                            table = outputTableInfo)
                    }
                }
                else {
                    withoutPreview
                }

            return withPreview
        }


        private data class RecordDisruptor(
            val disruptor: Disruptor<ReportOutputEvent<Any>>,
            val executor: ExecutorService
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val failed = AtomicBoolean(false)

    @Volatile
    private var cancelled = false

    private val preCachePartitions = PipelinePreCacheStage.partitions(preCachePartitionCount)

    private val summary = PipelineSummaryStage(
        ReportSummary(initialReportRunContext, initialReportRunContext.runDir))

    private var tableOutput: PipelineOutputTableStage? = null
    private var exportWriter: CompressedExportWriter? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun init(logicControl: LogicControl) {
        if (initialReportRunContext.output.type == OutputType.Explore) {
            tableOutput = PipelineOutputTableStage(
                TableReportOutput(
                    initialReportRunContext,
                    ReportOutputTrace(trace)))
        }
        else {
            exportWriter = CompressedExportWriter(
                initialReportRunContext.output.export)
        }

        logicControl.subscribeRequest(::pollRequest)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun beforeStart(arguments: TupleValue): Boolean {
//        if (nextDatasetInfo != null) {
//            return false
//        }
//
////        nextDatasetInfo = datasetInfo()
//        nextDatasetInfo = initialReportRunContext.datasetInfo
//            ?: return false

        return true
    }


    private fun <T> datasetDefinition(datasetInfo: DatasetInfo): DatasetDefinition<T> {
        val pluginCoordinates = datasetInfo.items.map { it.processorPluginCoordinate }.toSet()
        val classLoaderHandle = ServerContext.definitionRepository
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
        val definition = ServerContext.definitionRepository.define(
            processorDefinitionCoordinate, classLoaderHandle)

        @Suppress("UNCHECKED_CAST")
        return definition as ReportDefinition<T>
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun continueOrStart(
        logicControl: LogicControl,
        graphDefinition: GraphDefinition
    ): LogicResult {
        val datasetInfo = initialReportRunContext.datasetInfo
//            ?: return LogicResultFailed("Not initialized")

        datasetDefinition<Any>(datasetInfo).use { datasetDefinition ->
            val recordDisruptor = setupRecordDisruptor(
                datasetDefinition.classLoaderHandle/*, control*/)
            recordDisruptor.disruptor.start()

            val recordDisruptorInput = DisruptorPipelineOutput(recordDisruptor.disruptor.ringBuffer)
            try {
                for (flatDataContentDefinition in datasetDefinition.items) {
                    runFlatData(recordDisruptorInput, flatDataContentDefinition, logicControl)

                    if (failed.get() || cancelled) {
                        break
                    }
                }
            }
            finally {
                waitForProcessingToFinish(recordDisruptorInput)
                recordDisruptor.disruptor.shutdown()
                recordDisruptor.executor.shutdown()
                recordDisruptor.executor.awaitTermination(1, TimeUnit.MINUTES)
            }
        }

        return when {
            failed.get() -> {
                reportWorkPool.updateRunStatus(initialReportRunContext.runDir, OutputStatus.Failed)
                LogicResultFailed("error")
            }

            cancelled -> {
                reportWorkPool.updateRunStatus(initialReportRunContext.runDir, OutputStatus.Cancelled)
                LogicResultCancelled
            }

            else -> {
                reportWorkPool.updateRunStatus(initialReportRunContext.runDir, OutputStatus.Done)
                LogicResultSuccess(TupleValue.empty)
            }
        }
    }


    private fun <T> runFlatData(
        recordDisruptorInput: PipelineOutput<ReportOutputEvent<T>>,
        flatDataContentDefinition: FlatDataContentDefinition<T>,
        control: LogicControl
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

//        println("ReportExecution - start")
        var reachedEndOfData = false
        reportInputPipeline.start()
        try {
            reportInputTrace.startReading()

            while (! failed.get()) {
                if (control.pollCommand() == LogicCommand.Cancel) {
                    cancelled = true
                    break
                }

                val hasNext = reportInputPipeline.poll()
                if (! hasNext) {
                    reachedEndOfData = true
                    break
                }
            }
        }
        finally {
            reportInputPipeline.close(reachedEndOfData)

            waitForProcessingToFinish(recordDisruptorInput)

            val reachedEndWithoutFailOrCancel = ! failed.get() && ! cancelled
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
            initialReportRunContext.runDir.toString(),
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
                    withoutPreview.copy(
                        status = OutputStatus.Failed)
                }
                else {
                    withoutPreview.copy(
                        table = outputTableInfo)
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
        classLoaderHandle: ClassLoaderHandle,
    ): RecordDisruptor {
        val executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE)

        @Suppress("DEPRECATION")
        val recordDisruptor = Disruptor(
            { ReportOutputEvent<Any>() },
            recordDisruptorBufferSize,
            executor,
            recordProducerType,
            DisruptorUtils.newWaitStrategy()
        )

        val formulas = ReportFormulaStage(
            initialReportRunContext.dataType,
            initialReportRunContext.formula,
            classLoaderHandle.classLoader,
            ServerContext.calculatedColumnEval)

        var builder = recordDisruptor.handleEventsWith(formulas)

        val filter = ReportFilterStage(initialReportRunContext)
        if (! filter.isEmpty()) {
            builder = builder.then(filter)
        }

        if (initialReportRunContext.previewAll.enabled) {
            TODO("Preview All not implemented (yet)")
        }
        val previewEnabled = initialReportRunContext.previewFiltered.enabled

        val startOfOutput =
            if (tableOutput != null) {
                tableOutput
            }
            else {
                ExportColumnNormalizer(
                    initialReportRunContext.analysisColumnInfo.filteredColumns())
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
                    ExportFormat.byName(initialReportRunContext.output.export.format),
                    initialReportRunContext.analysisColumnInfo.filteredColumns(),
                    initialReportRunContext.reportDocumentName,
                    initialReportRunContext.output.export
                ))
                .then(CharsetExportEncoder(Charsets.UTF_8))
                .then(exportWriter)
        }

        val recordExceptionHandler = recordExceptionHandler(/*control*/)
        recordDisruptor.setDefaultExceptionHandler(recordExceptionHandler)

        return RecordDisruptor(recordDisruptor, executor)
    }


    private fun recordExceptionHandler(
//        control: LogicControl
    ): ExceptionHandler<ReportOutputEvent<*>> {
        return object : ExceptionHandler<ReportOutputEvent<*>> {
            override fun handleEventException(ex: Throwable, sequence: Long, event: ReportOutputEvent<*>) {
                if (failed.get()) {
                    return
                }
//
                logger.error("Record event - {}", event.row, ex)
                failed.set(true)
//                taskHandle?.terminalFailure(ExecutionFailure.ofException("Processing: ${event.row} - ", ex))
            }

            override fun handleOnStartException(ex: Throwable) {
                if (failed.get()) {
                    return
                }

                logger.error("Record start", ex)
                failed.set(true)
//                taskHandle?.terminalFailure(ExecutionFailure.ofException("Startup - ", ex))
            }
//
            override fun handleOnShutdownException(ex: Throwable) {
                if (failed.get()) {
                    return
                }

                logger.error("Record shutdown", ex)
                failed.set(true)
//                taskHandle?.terminalFailure(ExecutionFailure.ofException("Shutdown - ", ex))
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close(error: Boolean) {
        summary.close()
        tableOutput?.close(error)
        exportWriter?.close(/*error*/)

        if (error) {
            reportWorkPool.updateRunStatus(initialReportRunContext.runDir, OutputStatus.Failed)
        }
    }
}