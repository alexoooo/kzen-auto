package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.FlatRecordExtractor
import tech.kzen.auto.plugin.api.HeaderExtractor


data class ProcessorDefinition<Output>(
    val data: ProcessorDataDefinition<Output>,
    val headerExtractorFactory: () -> HeaderExtractor<Output>,
    val flatRecordExtractorFactory: () -> FlatRecordExtractor<Output>
) {
    //-----------------------------------------------------------------------------------------------------------------
//    init {
//        if (flatRecordExtractorFactory == null) {
//            val lastSegment = data.segments.last()
//            check(FlatRecordBuilder::class.java.isAssignableFrom(lastSegment.outputType)) {
//                "Output type is not compatible with ${FlatRecordBuilder::class.simpleName}, " +
//                        "please provide a flatRecordExtractorFactory"
//            }
//        }
//    }
}