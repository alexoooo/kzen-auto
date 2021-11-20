package tech.kzen.auto.server.objects.report.exec.input.model.instance

import tech.kzen.auto.plugin.api.ReportIntermediateStep
import tech.kzen.auto.plugin.api.ReportTerminalStep
import tech.kzen.auto.plugin.definition.ReportSegmentDefinition


class ReportSegmentInstance<Model, Output>(
    definition: ReportSegmentDefinition<Model, Output>
) {
    val modelFactory = definition.modelFactory
    val ringBufferSize = definition.ringBufferSize

    val intermediateStages: List<List<ReportIntermediateStep<Model>>> =
        definition
            .intermediateStepFactories
            .map { step ->
                step.intermediateStepFactories.map { it() }
            }

    val finalStage: ReportTerminalStep<Model, Output> =
            definition.finalStepFactory()
}