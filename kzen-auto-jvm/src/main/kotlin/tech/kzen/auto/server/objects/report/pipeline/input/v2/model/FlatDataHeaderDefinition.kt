package tech.kzen.auto.server.objects.report.pipeline.input.v2.model

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.api.managed.TraversableProcessorOutput
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.server.objects.report.pipeline.input.v2.ProcessorInputChain
import tech.kzen.auto.server.objects.report.pipeline.input.v2.ProcessorInputReader
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataSource


data class FlatDataHeaderDefinition<T>(
    val dataLocationInfo: DataLocationInfo,
    val flatDataSource: FlatDataSource,
    val processorDefinition: ProcessorDefinition<T>
) {
    fun openInputChain(dataBlockSize: Int): ProcessorInputChain<T> {
        val flatDataStream = flatDataSource.open(dataLocationInfo)

        return ProcessorInputChain(
            ProcessorInputReader(flatDataStream),
            processorDefinition.processorDataDefinition,
            dataLocationInfo.dataEncoding.textEncoding?.charset,
            dataBlockSize)
    }


    fun extract(traversable: TraversableProcessorOutput<T>): HeaderListing {
        val headerExtractor = processorDefinition.headerExtractorFactory()
        val columnNames = headerExtractor.extract(traversable)
        return HeaderListing(columnNames)
    }
}