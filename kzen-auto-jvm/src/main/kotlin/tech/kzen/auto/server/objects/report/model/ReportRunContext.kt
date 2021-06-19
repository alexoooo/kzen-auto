package tech.kzen.auto.server.objects.report.model

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.DatasetInfo
import tech.kzen.lib.platform.ClassName


data class ReportRunContext(
    val dataType: ClassName,
    val datasetInfo: DatasetInfo,
    val formula: FormulaSpec,
    val previewAll: PreviewSpec,
    val filter: FilterSpec,
    val previewFiltered: PreviewSpec,
    val analysis: AnalysisSpec
) {
    val inputAndFormulaColumns: HeaderListing by lazy {
        datasetInfo.headerSuperset().append(formula.headerListing())
    }


    fun toSignature(): ReportRunSignature {
        return ReportRunSignature(
            datasetInfo,
            inputAndFormulaColumns,
            formula,
            filter.toRunSignature(),
            analysis.toRunSignature()
        )
    }


//    fun toFormulaSignature(): ReportFormulaSignature {
//        return ReportFormulaSignature(
//            datasetInfo.headerSuperset(), formula, dataType)
//    }
}