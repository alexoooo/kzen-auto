package tech.kzen.auto.server.objects.pipeline

import com.lmax.disruptor.ExceptionHandler
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
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
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.objects.pipeline.exec.PipelineProcessor
import tech.kzen.auto.server.objects.pipeline.exec.input.connect.file.FileFlatDataSource
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.DatasetDefinition
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.DatasetInfo
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.FlatDataContentDefinition
import tech.kzen.auto.server.objects.pipeline.exec.input.model.instance.ProcessorDataInstance
import tech.kzen.auto.server.objects.pipeline.exec.input.stages.ProcessorInputReader
import tech.kzen.auto.server.objects.pipeline.exec.output.TableReportOutput
import tech.kzen.auto.server.objects.pipeline.exec.output.export.CharsetExportEncoder
import tech.kzen.auto.server.objects.pipeline.exec.output.export.CompressedExportWriter
import tech.kzen.auto.server.objects.pipeline.exec.output.export.ExportColumnNormalizer
import tech.kzen.auto.server.objects.pipeline.exec.output.export.format.ExportFormatter
import tech.kzen.auto.server.objects.pipeline.exec.output.export.model.ExportFormat
import tech.kzen.auto.server.objects.pipeline.exec.stages.*
import tech.kzen.auto.server.objects.pipeline.exec.trace.PipelineInputTrace
import tech.kzen.auto.server.objects.pipeline.exec.trace.PipelineOutputTrace
import tech.kzen.auto.server.objects.pipeline.model.ReportRunContext
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.event.output.DisruptorPipelineOutput
import tech.kzen.auto.server.objects.report.pipeline.summary.ReportSummary
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.auto.server.util.DisruptorUtils
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean


class PipelineExecution(
    private val initialReportRunContext: ReportRunContext,
    private val reportWorkPool: ReportWorkPool,
    private val trace: LogicTraceHandle,
    private val runExecutionId: LogicRunExecutionId
):
    LogicExecution
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(PipelineExecution::class.java)


//        private const val preCachePartitionCount = 0
//        private const val preCachePartitionCount = 1
//        private const val preCachePartitionCount = 2
        private const val preCachePartitionCount = 3
//        private const val preCachePartitionCount = 4

//        private const val recordDisruptorBufferSize = 16 * 1024
        private const val recordDisruptorBufferSize = 32 * 1024
//        private const val recordDisruptorBufferSize = 64 * 1024


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
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val failed = AtomicBoolean(false)

    @Volatile
    private var cancelled = false

    private val preCachePartitions = ProcessorPreCacheStage.partitions(preCachePartitionCount)

    private val summary = ProcessorSummaryStage(
        ReportSummary(initialReportRunContext, initialReportRunContext.runDir))

    private var tableOutput: ProcessorOutputTableStage? = null
    private var exportWriter: CompressedExportWriter? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun init(logicControl: LogicControl) {
        if (initialReportRunContext.output.type == OutputType.Explore) {
            tableOutput = ProcessorOutputTableStage(
                TableReportOutput(
                    initialReportRunContext,
                    PipelineOutputTrace(trace)))
        }
        else {
            exportWriter = CompressedExportWriter(
                initialReportRunContext.reportDocumentName,
                initialReportRunContext.output.export)
        }

        logicControl.subscribeRequest(::pollRequest)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun next(arguments: TupleValue): Boolean {
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
            .classLoaderHandle(pluginCoordinates, ClassLoader.getSystemClassLoader())

        val cache = mutableMapOf<PluginCoordinate, ProcessorDefinition<T>>()
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
                    processorDataDefinition)
            )
        }

        builder.sortBy { it.flatDataInfo }

        return DatasetDefinition(builder, classLoaderHandle)
    }


    private fun <T> processorDataDefinition(
        processorDefinitionCoordinate: PluginCoordinate,
        classLoaderHandle: ClassLoaderHandle
    ): ProcessorDefinition<T> {
        val definition = ServerContext.definitionRepository.define(
            processorDefinitionCoordinate, classLoaderHandle)

        @Suppress("UNCHECKED_CAST")
        return definition as ProcessorDefinition<T>
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun run(control: LogicControl): LogicResult {
        val datasetInfo = initialReportRunContext.datasetInfo
//            ?: return LogicResultFailed("Not initialized")

        datasetDefinition<Any>(datasetInfo).use { datasetDefinition ->
            val recordDisruptor = setupRecordDisruptor(
                datasetDefinition.classLoaderHandle/*, control*/)
            recordDisruptor.start()

            try {
                val recordDisruptorInput = DisruptorPipelineOutput(recordDisruptor.ringBuffer)

                for (flatDataContentDefinition in datasetDefinition.items) {
                    runFlatData(recordDisruptorInput, flatDataContentDefinition, control)

                    if (failed.get() || cancelled) {
                        break
                    }
                }
            }
            finally {
                recordDisruptor.shutdown()
//                progressTracker.finish()
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
        recordDisruptorInput: PipelineOutput<ProcessorOutputEvent<T>>,
        flatDataContentDefinition: FlatDataContentDefinition<T>,
        control: LogicControl
    ) {
        val flatDataStream = flatDataContentDefinition.open()
        val totalSize = flatDataContentDefinition.size()

        val flatDataLocation = flatDataContentDefinition.flatDataInfo.flatDataLocation
        val pipelineInputTrace = PipelineInputTrace(trace, flatDataLocation.dataLocation, totalSize)

        val processorInputReader = ProcessorInputReader(flatDataStream, pipelineInputTrace)

        val processorDataInstance = ProcessorDataInstance(
            flatDataContentDefinition.processorDefinition.processorDataDefinition)

        val processorInputPipeline = PipelineProcessor(
            processorInputReader,
            recordDisruptorInput,
            processorDataInstance,
            flatDataLocation.dataEncoding,
            flatDataContentDefinition.flatDataInfo,
            pipelineInputTrace,
            failed)

        processorInputPipeline.start()
        try {
            pipelineInputTrace.startReading()

            while (! failed.get()) {
                if (control.pollCommand() == LogicCommand.Cancel) {
                    cancelled = true
                    break
                }

                val hasNext = processorInputPipeline.poll()

                if (! hasNext) {
                    break
                }
            }
        }
        finally {
            processorInputPipeline.close()

            val reachedEnd = ! failed.get() && ! cancelled
            pipelineInputTrace.finishParsing(reachedEnd)
        }
    }


    private fun pollRequest(executionRequest: ExecutionRequest): ExecutionResult {
        val action = executionRequest.getSingle(PipelineConventions.actionParameter)
            ?: return ExecutionResult.failure("Missing action")

        return when (action) {
            PipelineConventions.actionOutputInfoOnline ->
                pollOutputInfoRequest(executionRequest)

            PipelineConventions.actionSummaryOnline ->
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
//        control: LogicControl
    ): Disruptor<ProcessorOutputEvent<Any>> {
        val recordDisruptor = Disruptor(
            { ProcessorOutputEvent<Any>() },
            recordDisruptorBufferSize,
            DaemonThreadFactory.INSTANCE,
            ProducerType.SINGLE,
            DisruptorUtils.newWaitStrategy()
        )

        val formulas = ProcessorFormulaStage(
            initialReportRunContext.dataType,
            initialReportRunContext.formula,
            classLoaderHandle.classLoader,
            ServerContext.calculatedColumnEval)

        var builder = recordDisruptor.handleEventsWith(formulas)

        val filter = ProcessorFilterStage(initialReportRunContext)
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
                    initialReportRunContext.analysisColumnInfo.filteredColumns()
                ))
                .then(CharsetExportEncoder(Charsets.UTF_8))
                .then(exportWriter)
        }

        val recordExceptionHandler = recordExceptionHandler(/*control*/)
        recordDisruptor.setDefaultExceptionHandler(recordExceptionHandler)

        return recordDisruptor
    }


    private fun recordExceptionHandler(
//        control: LogicControl
    ): ExceptionHandler<ProcessorOutputEvent<*>> {
        return object : ExceptionHandler<ProcessorOutputEvent<*>> {
            override fun handleEventException(ex: Throwable, sequence: Long, event: ProcessorOutputEvent<*>) {
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
        exportWriter?.close(error)

        if (error) {
            reportWorkPool.updateRunStatus(initialReportRunContext.runDir, OutputStatus.Failed)
        }
    }
}