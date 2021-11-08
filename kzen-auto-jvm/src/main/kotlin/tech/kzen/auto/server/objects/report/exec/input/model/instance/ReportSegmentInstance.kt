package tech.kzen.auto.server.objects.report.exec.input.model.instance

import tech.kzen.auto.plugin.api.ReportInputIntermediateStep
import tech.kzen.auto.plugin.api.ReportInputTerminalStep
import tech.kzen.auto.plugin.definition.ReportSegmentDefinition


class ReportSegmentInstance<Model, Output>(
    definition: ReportSegmentDefinition<Model, Output>
) {
    val modelFactory = definition.modelFactory
    val ringBufferSize = definition.ringBufferSize

    val intermediateStages: List<List<ReportInputIntermediateStep<Model>>> =
        definition
            .intermediateStepFactories
            .map { step ->
                step.intermediateStepFactories.map { it() }
            }

    val finalStage: ReportInputTerminalStep<Model, Output> =
            definition.finalStepFactory()
}