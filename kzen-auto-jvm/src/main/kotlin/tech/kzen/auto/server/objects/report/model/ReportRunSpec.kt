package tech.kzen.auto.server.objects.report.model

import tech.kzen.auto.common.objects.document.report.spec.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PivotSpec
import java.nio.file.Path


data class ReportRunSpec(
    val inputs: List<Path>,
    val columnNames: List<String>,
    val formula: FormulaSpec,
    val filter: FilterSpec,
    val pivot: PivotSpec
) {
    fun toSignature(): ReportRunSignature {
        return ReportRunSignature(
            inputs,
            columnNames,
            formula,
            filter.filterNonEmpty(),
            pivot.rows,
            pivot.values.columns.keys
        )
    }

    fun toFormulaSignature(): ReportFormulaSignature {
        return ReportFormulaSignature(columnNames, formula)
    }
}