package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.plugin.model.DataInputEvent


data class ReportDataDefinition<Output>(
    val dataFramerFactory: () -> DataFramer,
    val outputModelType: Class<Output>,
    val segments: List<ReportSegmentDefinition<*, *>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    init {
        require(segments.isNotEmpty())

        val firstSegment = segments.first()
        require(DataInputEvent::class.java.isAssignableFrom(firstSegment.modelType())) {
            "Input must extend ${DataInputEvent::class.simpleName}"
        }

        val lastSegment = segments.last()
        require(outputModelType == lastSegment.outputPayloadType) {
            "Last ProcessorSegment output must be " +
                    "${outputModelType.simpleName} (it was ${lastSegment.outputPayloadType.simpleName})"
        }

        for (i in 0 .. segments.size - 2) {
            val segment = segments[i]
            val nextSegment = segments[i + 1]

            require(segment.outputPayloadType == nextSegment.modelType()) {
                "Pipeline number ${i + 1} has output type ${segment.outputPayloadType.simpleName} " +
                        "which is not compatible with next pipeline's model type ${nextSegment.modelType().simpleName}"
            }
        }
    }
}