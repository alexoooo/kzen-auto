package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.ReportInputIntermediateStep


data class ReportSegmentStepDefinition<Model>(
    val intermediateStepFactories: List<() -> ReportInputIntermediateStep<Model>>
) {
//    companion object {
//        fun <Model> of(
//            vararg factories: () -> PipelineIntermediateStep<Model>
//        ): ProcessorSegmentStepDefinition<Model> {
//            return ProcessorSegmentStepDefinition(listOf(*factories))
//        }
//    }
}