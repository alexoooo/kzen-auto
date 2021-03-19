package tech.kzen.auto.server.objects.report.pipeline.input.model.data

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.api.managed.TraversableProcessorOutput
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.server.objects.report.pipeline.input.ProcessorInputChain
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FlatDataSource
import tech.kzen.auto.server.objects.report.pipeline.input.stages.ProcessorInputReader


data class FlatDataHeaderDefinition<T>(
    val flatDataLocation: FlatDataLocation,
    val flatDataSource: FlatDataSource,
    val processorDefinition: ProcessorDefinition<T>
) {
    fun openInputChain(dataBlockSize: Int): ProcessorInputChain<T> {
        val flatDataStream = flatDataSource.open(flatDataLocation)

        return ProcessorInputChain(
            ProcessorInputReader(flatDataStream),
            processorDefinition.processorDataDefinition,
            flatDataLocation.dataEncoding.textEncoding?.charset,
            dataBlockSize)
    }


    fun extract(traversable: TraversableProcessorOutput<T>): HeaderListing {
        val headerExtractor = processorDefinition.headerExtractorFactory()
        val columnNames = headerExtractor.extract(traversable)
        return HeaderListing(columnNames)
    }
}