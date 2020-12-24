package tech.kzen.auto.server.objects.report.model

import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec


data class ReportFormulaSignature(
    val columnNames: List<String>,
    val formula: FormulaSpec
)