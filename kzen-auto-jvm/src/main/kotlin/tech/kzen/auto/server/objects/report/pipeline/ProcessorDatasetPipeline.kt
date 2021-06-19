package tech.kzen.auto.server.objects.report.pipeline

import com.lmax.disruptor.ExceptionHandler
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.plugin.api.managed.PipelineOutput
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.event.output.DisruptorPipelineOutput
import tech.kzen.auto.server.objects.report.pipeline.input.ProcessorInputPipeline
import tech.kzen.auto.server.objects.report.pipeline.input.connect.file.FileFlatDataSource
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.DatasetDefinition
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.FlatDataContentDefinition
import tech.kzen.auto.server.objects.report.pipeline.input.model.instance.ProcessorDataInstance
import tech.kzen.auto.server.objects.report.pipeline.input.stages.ProcessorInputReader
import tech.kzen.auto.server.objects.report.pipeline.output.ReportOutput
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgressTracker
import tech.kzen.auto.server.objects.report.pipeline.stages.*
import tech.kzen.auto.server.objects.report.pipeline.summary.ReportSummary
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.util.DisruptorUtils
import java.io.InputStream
import java.nio.file.Path


class ProcessorDatasetPipeline(
    private val initialReportRunContext: ReportRunContext,
    runDir: Path,
    private val reportWorkPool: ReportWorkPool,
    private val taskHandle: TaskHandle?
):
    TaskRun
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ProcessorDatasetPipeline::class.java)

//        private val payloadType = FlatDataRecord.className


//        private const val preCachePartitionCount = 0
        private const val preCachePartitionCount = 1
//        private const val preCachePartitionCount = 2
//        private const val preCachePartitionCount = 3
//        private const val preCachePartitionCount = 4

//        private const val recordDisruptorBufferSize = 16 * 1024
        private const val recordDisruptorBufferSize = 32 * 1024
//        private const val recordDisruptorBufferSize = 64 * 1024


        fun passivePreview(
            reportRunContext: ReportRunContext,
            runDir: Path,
            outputSpec: OutputExploreSpec,
            reportWorkPool: ReportWorkPool
        ): OutputInfo {
            return usePassive(reportRunContext, runDir, reportWorkPool) {
                it.outputPreview(reportRunContext, outputSpec)!!
            }
        }


        fun passiveSave(
            reportRunContext: ReportRunContext,
            runDir: Path,
            outputSpec: OutputExploreSpec,
            reportWorkPool: ReportWorkPool
        ): Path {
            return usePassive(reportRunContext, runDir, reportWorkPool) {
                it.outputSave(reportRunContext, outputSpec)
            }
        }


        fun passiveDownload(
            reportRunContext: ReportRunContext,
            runDir: Path,
            outputSpec: OutputExploreSpec,
            reportWorkPool: ReportWorkPool
        ): InputStream {
            return usePassive(reportRunContext, runDir, reportWorkPool) {
                it.outputDownload(reportRunContext, outputSpec)
            }
        }


        private fun <T> usePassive(
            reportRunContext: ReportRunContext,
            runDir: Path,
            reportWorkPool: ReportWorkPool,
            user: (ProcessorDatasetPipeline) -> T
        ): T {
            val instance = ofPassive(reportRunContext, runDir, reportWorkPool)
            var error = true
            return try {
                val value = user(instance)
                error = false
                value
            }
            finally {
                instance.close(error)
            }
        }


        private fun ofPassive(
            reportRunContext: ReportRunContext,
            runDir: Path,
            reportWorkPool: ReportWorkPool
        ): ProcessorDatasetPipeline {
            return ProcessorDatasetPipeline(
//                DataTypeProvider.any,
                reportRunContext,
                runDir,
                reportWorkPool,
                null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val progressTracker: ReportProgressTracker = ReportProgressTracker(
        initialReportRunContext.datasetInfo.dataLocations(), taskHandle)

    private val preCachePartitions = ProcessorPreCacheStage.partitions(preCachePartitionCount)

    private val summary = ProcessorSummaryStage(
        ReportSummary(initialReportRunContext, runDir, taskHandle))

    private val output = ProcessorOutputStage(
        ReportOutput(initialReportRunContext, runDir, taskHandle, progressTracker),
        reportWorkPool)


    //-----------------------------------------------------------------------------------------------------------------
    fun run() {
        val handle = taskHandle!!

        datasetDefinition<Any>().use { datasetDefinition ->
            val recordDisruptor = setupRecordDisruptor(datasetDefinition.classLoaderHandle)
            recordDisruptor.start()

            try {
                val recordDisruptorInput = DisruptorPipelineOutput(recordDisruptor.ringBuffer)

                for (flatDataContentDefinition in datasetDefinition.items) {
                    runFlatData(recordDisruptorInput, flatDataContentDefinition, handle)

                    if (handle.stopRequested()) {
                        break
                    }
                }
            }
            finally {
                recordDisruptor.shutdown()
                progressTracker.finish()
            }
        }
    }


    private fun <T> runFlatData(
        recordDisruptorInput: PipelineOutput<ProcessorOutputEvent<T>>,
        flatDataContentDefinition: FlatDataContentDefinition<T>,
        handle: TaskHandle
    ) {
        val flatDataStream = flatDataContentDefinition.open()
        val totalSize = flatDataContentDefinition.size()

        val flatDataLocation = flatDataContentDefinition.flatDataInfo.flatDataLocation
        val streamProgressTracker = progressTracker.getInitial(flatDataLocation.dataLocation, totalSize)

        val processorInputReader = ProcessorInputReader(flatDataStream, streamProgressTracker)
        val processorDataInstance = ProcessorDataInstance(
            flatDataContentDefinition.processorDefinition.processorDataDefinition)

        val processorInputPipeline = ProcessorInputPipeline(
            processorInputReader,
            recordDisruptorInput,
            processorDataInstance,
            flatDataLocation.dataEncoding,
            flatDataContentDefinition.flatDataInfo.headerListing,
            streamProgressTracker,
            handle)

        processorInputPipeline.start()
        try {
            streamProgressTracker.startReading()

            while (! handle.stopRequested()) {
                val hasNext = processorInputPipeline.poll()

                if (! hasNext) {
                    break
                }
            }
        }
        finally {
            processorInputPipeline.close()
            streamProgressTracker.finishParsing()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun <T> datasetDefinition(): DatasetDefinition<T> {
        val pluginCoordinates = initialReportRunContext.datasetInfo.items.map { it.processorPluginCoordinate }.toSet()
        val classLoaderHandle = ServerContext.definitionRepository
            .classLoaderHandle(pluginCoordinates, ClassLoader.getSystemClassLoader())

        val cache = mutableMapOf<PluginCoordinate, ProcessorDefinition<T>>()
        val builder = mutableListOf<FlatDataContentDefinition<T>>()

        for (flatDataInfo in initialReportRunContext.datasetInfo.items) {
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

        return DatasetDefinition(builder, classLoaderHandle)
    }


    private fun <T> processorDataDefinition(
        processorDefinitionCoordinate: PluginCoordinate,
        classLoaderHandle: ClassLoaderHandle
    ): ProcessorDefinition<T> {
        val definition = ServerContext.definitionRepository.define(processorDefinitionCoordinate, classLoaderHandle)

        @Suppress("UNCHECKED_CAST")
        return definition as ProcessorDefinition<T>
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun setupRecordDisruptor(
        classLoaderHandle: ClassLoaderHandle
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

        builder
            .then(*preCachePartitions)
            .then(summary, output)

        recordDisruptor.setDefaultExceptionHandler(recordExceptionHandler())

        return recordDisruptor
    }


    private fun recordExceptionHandler(): ExceptionHandler<ProcessorOutputEvent<*>> {
        return object : ExceptionHandler<ProcessorOutputEvent<*>> {
            override fun handleEventException(ex: Throwable, sequence: Long, event: ProcessorOutputEvent<*>) {
                if (taskHandle?.isFailed() == true) {
                    return
                }

                logger.error("Record event - {}", event.row, ex)
                taskHandle?.terminalFailure(ExecutionFailure.ofException("Processing: ${event.row} - ", ex))
            }

            override fun handleOnStartException(ex: Throwable) {
                if (taskHandle?.isFailed() == true) {
                    return
                }

                logger.error("Record start", ex)
                taskHandle?.terminalFailure(ExecutionFailure.ofException("Startup - ", ex))
            }

            override fun handleOnShutdownException(ex: Throwable) {
                if (taskHandle?.isFailed() == true) {
                    return
                }

                logger.error("Record shutdown", ex)
                taskHandle?.terminalFailure(ExecutionFailure.ofException("Shutdown - ", ex))
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun outputPreview(
        reportRunContext: ReportRunContext,
        outputSpec: OutputExploreSpec
    ): OutputInfo? {
        if (taskHandle?.isFailed() == true) {
            taskHandle.awaitTerminal()
            return null
        }

        return output.reportOutput.preview(reportRunContext, outputSpec, reportWorkPool)
    }


    private fun outputSave(reportRunContext: ReportRunContext, outputSpec: OutputExploreSpec): Path {
        return output.reportOutput.save(reportRunContext, outputSpec)
    }


    private fun outputDownload(reportRunContext: ReportRunContext, outputSpec: OutputExploreSpec): InputStream {
        return output.reportOutput.download(reportRunContext, outputSpec)
    }


    fun summaryView(): TableSummary? {
        if (taskHandle?.isFailed() == true) {
            taskHandle.awaitTerminal()
            return null
        }
        return summary.reportSummary.view()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close(error: Boolean) {
        summary.close()
        output.close(error)
//        classLoaderHandle.close()
    }
}