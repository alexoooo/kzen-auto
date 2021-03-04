package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.PipelineIntermediateStep
import tech.kzen.auto.plugin.api.PipelineTerminalStep


data class ProcessorSegmentDefinition<Model, Output>(
        val modelFactory: () -> Model,
        val outputPayloadType: Class<*>,
        val intermediateStageFactories: List<() -> PipelineIntermediateStep<Model>>,
        val finalStageFactory: () -> PipelineTerminalStep<Model, Output>,
) {
    fun modelType(): Class<Model> {
        @Suppress("UNCHECKED_CAST")
        return modelFactory()!!::class.java as Class<Model>
    }
}
