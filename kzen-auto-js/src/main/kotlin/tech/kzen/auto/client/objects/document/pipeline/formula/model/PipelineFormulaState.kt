package tech.kzen.auto.client.objects.document.pipeline.formula.model


data class PipelineFormulaState(
    val formulaLoading: Boolean = false,
    val formulaError: String? = null,
    val formulaMessages: Map<String, String> = mapOf()
)