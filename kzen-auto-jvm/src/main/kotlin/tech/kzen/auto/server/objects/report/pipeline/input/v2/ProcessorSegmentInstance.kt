package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.api.PipelineIntermediateStep
import tech.kzen.auto.plugin.api.PipelineTerminalStep
import tech.kzen.auto.plugin.definition.ProcessorSegmentDefinition


class ProcessorSegmentInstance<Model, Output>(
        definition: ProcessorSegmentDefinition<Model, Output>
) {
    val intermediateStages: List<PipelineIntermediateStep<Model>> =
            definition.intermediateStageFactories.map { it() }

    val finalStage: PipelineTerminalStep<Model, Output> =
            definition.finalStageFactory()
}