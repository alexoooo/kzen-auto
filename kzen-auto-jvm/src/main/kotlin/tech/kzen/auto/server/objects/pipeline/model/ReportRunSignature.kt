package tech.kzen.auto.server.objects.pipeline.model

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.DatasetInfo
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ReportRunSignature(
    val datasetInfo: DatasetInfo,
    val inputAndFormulaColumns: HeaderListing,
    val formula: FormulaSpec,
    val filterSignature: FilterSpec,
    val analysisSignature: AnalysisSpec
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    fun hasPivot(): Boolean {
        return analysisSignature.type == AnalysisType.PivotTable
//        return pivotRows.values.isNotEmpty() ||
//                pivotValues.values.isNotEmpty()
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(datasetInfo)

        sink.addDigestible(formula)
        sink.addDigestible(filterSignature)
        sink.addDigestible(analysisSignature)
    }
}