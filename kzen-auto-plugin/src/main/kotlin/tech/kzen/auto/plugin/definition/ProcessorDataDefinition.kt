package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.ModelOutputEvent


data class ProcessorDataDefinition<Output>(
    val dataFramerFactory: () -> DataFramer,
    val outputFactory: () -> ModelOutputEvent<Output>,
    val segments: List<ProcessorSegmentDefinition<*, *>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    init {
        require(segments.isNotEmpty())

        val firstSegment = segments.first()
        require(DataInputEvent::class.java.isAssignableFrom(firstSegment.modelType)) {
            "Input must extend ${DataInputEvent::class.simpleName}"
        }

        val sampleOutput = outputFactory()
        val lastSegment = segments.last()
        require(sampleOutput::class == lastSegment.outputType) {
            "Last ProcessorSegment output must be " +
                    "${sampleOutput::class.simpleName} (it was ${lastSegment.outputType.simpleName})"
        }

        for (i in 0 .. segments.size - 2) {
            val segment = segments[i]
            val nextSegment = segments[i + 1]

            require(segment.outputType == nextSegment.modelType) {
                "Pipeline number ${i + 1} has output type ${segment.outputType.simpleName} " +
                        "which is not compatible with next pipeline's model type ${nextSegment.modelType.simpleName}"
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun newInputEvent(): DataInputEvent {
        val factory = segments.first().modelFactory

        // checked in init
        @Suppress("UNCHECKED_CAST")
        factory as () -> DataInputEvent

        return factory()
    }


    fun outputModelType(): Class<Output> {
        return outputFactory().type
    }
}