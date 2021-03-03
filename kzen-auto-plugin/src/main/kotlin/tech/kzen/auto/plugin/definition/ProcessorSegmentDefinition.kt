package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.PipelineIntermediateStep
import tech.kzen.auto.plugin.api.PipelineTerminalStep


data class ProcessorSegmentDefinition<Model, Output>(
    val modelType: Class<Model>,
    val outputType: Class<Output>,
    val modelFactory: () -> Model,
    val intermediateStageFactories: List<() -> PipelineIntermediateStep<Model>>,
    val finalStageFactory: () -> PipelineTerminalStep<Model, Output>
)
