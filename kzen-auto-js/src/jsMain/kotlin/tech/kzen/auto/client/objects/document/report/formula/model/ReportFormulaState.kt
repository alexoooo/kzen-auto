package tech.kzen.auto.client.objects.document.report.formula.model


data class ReportFormulaState(
    val formulaLoading: Boolean = false,
    val formulaError: String? = null,
    val formulaMessages: Map<String, String> = mapOf()
)