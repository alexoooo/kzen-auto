package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.ReportIntermediateStep


data class ReportSegmentStepDefinition<Model>(
    val intermediateStepFactories: List<() -> ReportIntermediateStep<Model>>
) {
//    companion object {
//        fun <Model> of(
//            vararg factories: () -> PipelineIntermediateStep<Model>
//        ): ProcessorSegmentStepDefinition<Model> {
//            return ProcessorSegmentStepDefinition(listOf(*factories))
//        }
//    }
}