package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.PipelineIntermediateStep


data class ProcessorSegmentStepDefinition<Model>(
    val intermediateStepFactories: List<() -> PipelineIntermediateStep<Model>>
) {
//    companion object {
//        fun <Model> of(
//            vararg factories: () -> PipelineIntermediateStep<Model>
//        ): ProcessorSegmentStepDefinition<Model> {
//            return ProcessorSegmentStepDefinition(listOf(*factories))
//        }
//    }
}