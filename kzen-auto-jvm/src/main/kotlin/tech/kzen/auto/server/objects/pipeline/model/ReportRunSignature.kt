package tech.kzen.auto.server.objects.pipeline.model

import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.DatasetInfo
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ReportRunSignature(
    val datasetInfo: DatasetInfo,
    val formula: FormulaSpec,
    val filterSignature: FilterSpec,
    val analysisSignature: AnalysisSpec,
//    val filteredColumns: HeaderListing
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
//    fun hasPivot(): Boolean {
//        return analysisSignature.type == AnalysisType.PivotTable
//    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(datasetInfo)

        sink.addDigestible(formula)
        sink.addDigestible(filterSignature)
        sink.addDigestible(analysisSignature)
    }
}