package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.ReportTerminalStep


data class ReportSegmentDefinition<Model, Output>(
    val modelFactory: () -> Model,
    val outputPayloadType: Class<*>,
    val intermediateStepFactories: List<ReportSegmentStepDefinition<Model>>,
    val finalStepFactory: () -> ReportTerminalStep<Model, Output>,
    val ringBufferSize: Int
) {
    init {
        require(ringBufferSize != 0 && (ringBufferSize and ringBufferSize - 1) == 0) {
            "Ring buffer size must be a power of 2: $ringBufferSize"
        }
    }


    fun modelType(): Class<Model> {
        @Suppress("UNCHECKED_CAST")
        return modelFactory()!!::class.java as Class<Model>
    }
}
