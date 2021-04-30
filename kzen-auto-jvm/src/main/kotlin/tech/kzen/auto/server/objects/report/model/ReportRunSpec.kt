package tech.kzen.auto.server.objects.report.model

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PivotSpec
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.DatasetInfo


data class ReportRunSpec(
    val datasetInfo: DatasetInfo,
    val formula: FormulaSpec,
    val filter: FilterSpec,
    val pivot: PivotSpec
) {
    val inputAndFormulaColumns: HeaderListing by lazy {
        datasetInfo.headerSuperset().append(formula.headerListing())
    }


    fun toSignature(): ReportRunSignature {
        return ReportRunSignature(
            datasetInfo,
            inputAndFormulaColumns,
            formula,
            filter.filterNonEmpty(),
            pivot.rows,
            pivot.values.headerListing()
        )
    }


    fun toFormulaSignature(): ReportFormulaSignature {
        return ReportFormulaSignature(
            datasetInfo.headerSuperset(), formula)
    }
}