package tech.kzen.auto.server.objects.pipeline

import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.PluginUtils.asPluginCoordinate
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.auto.server.objects.report.ReportUtils
import tech.kzen.auto.server.objects.report.group.GroupPattern
import tech.kzen.auto.server.objects.report.pipeline.input.connect.file.FileFlatDataSource
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.*
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.model.*


class PipelineExecution(
    private val input: InputSpec
): LogicExecution {
    private var nextDatasetInfo: DatasetInfo? = null


    override fun next(arguments: TupleValue): Boolean {
        if (nextDatasetInfo != null) {
//            return "Already ran"
            return false
        }

        val groupPattern = GroupPattern.parse(input.selection.groupBy)
            ?: GroupPattern.empty

        val items = mutableListOf<FlatDataInfo>()
        for (inputDataSpec in input.selection.locations) {
            val dataLocation = inputDataSpec.location

            val pluginCoordinate = inputDataSpec.processorDefinitionCoordinate.asPluginCoordinate()
            val processorDefinitionMetadata = ServerContext.definitionRepository.metadata(pluginCoordinate)
//                ?: return "Not found: $pluginCoordinate"
                ?: return false

            val dataEncoding = ReportUtils.encodingWithMetadata(inputDataSpec, processorDefinitionMetadata)

            val flatDataLocation = FlatDataLocation(
                dataLocation, dataEncoding)

            val cachedHeaderListing = ServerContext.columnListingAction.cachedHeaderListing(
                dataLocation, pluginCoordinate)

            val headerListing = cachedHeaderListing
                ?: run {
                    val classLoaderHandle = ServerContext.definitionRepository
                        .classLoaderHandle(setOf(pluginCoordinate), ClassLoader.getSystemClassLoader())

                    classLoaderHandle.use {
                        val processorDefinition = ServerContext.definitionRepository.define(
                            pluginCoordinate, it)

                        ServerContext.columnListingAction.headerListing(
                            FlatDataHeaderDefinition(
                                flatDataLocation,
                                FileFlatDataSource(),
                                processorDefinition),
                            pluginCoordinate
                        )
                    }
                }

            val fileGroup = groupPattern.extract(flatDataLocation.dataLocation.fileName())

            items.add(FlatDataInfo(flatDataLocation, headerListing, pluginCoordinate, fileGroup))
        }
        nextDatasetInfo = DatasetInfo(items.sorted())

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


    override fun run(control: LogicControl): LogicResult {
        val datasetInfo = nextDatasetInfo
            ?: return LogicResultFailed("Not initialized")

        var cancelled = false

        datasetDefinition<Any>(datasetInfo).use { datasetDefinition ->
//            val recordDisruptor = setupRecordDisruptor(datasetDefinition.classLoaderHandle)
//            recordDisruptor.start()

            try {
//                val recordDisruptorInput = DisruptorPipelineOutput(recordDisruptor.ringBuffer)

                val buffer = ByteArray(1024)

                for (flatDataContentDefinition in datasetDefinition.items) {
                    val flatDataStream = flatDataContentDefinition.open()

                    while (true) {
                        val result = flatDataStream.read(buffer)
                        println("result: $result")

                        if (result.isEndOfData()) {
                            flatDataStream.close()
                            break
                        }

                        if (control.pollCommand() == LogicCommand.Cancel) {
                            cancelled = true
                            flatDataStream.close()
                            break
                        }
                    }
                }
            }
            finally {
//                recordDisruptor.shutdown()
//                progressTracker.finish()
            }
        }

        return when {
            cancelled -> LogicResultCancelled
            else -> LogicResultSuccess(TupleValue.empty)
        }
    }
}