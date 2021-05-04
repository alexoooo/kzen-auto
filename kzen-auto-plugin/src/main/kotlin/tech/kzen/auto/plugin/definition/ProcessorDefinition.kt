package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.HeaderExtractor


data class ProcessorDefinition<Output>(
    val processorDataDefinition: ProcessorDataDefinition<Output>,
    val headerExtractorFactory: () -> HeaderExtractor<Output>/*,
    val closer: AutoCloseable = AutoCloseable {}*/
)/*:
    AutoCloseable*/
{
//    override fun close() {
//        closer.close()
//    }
}