package tech.kzen.auto.server.objects.pipeline.model

import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.DatasetInfo
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.platform.ClassName
import java.nio.file.Path


data class ReportRunContext(
    val runDir: Path,
    val reportDocumentName: DocumentName,
    val dataType: ClassName,
    val datasetInfo: DatasetInfo,
    val analysisColumnInfo: AnalysisColumnInfo,
    val formula: FormulaSpec,
    val previewAll: PreviewSpec,
    val filter: FilterSpec,
    val previewFiltered: PreviewSpec,
    val analysis: AnalysisSpec,
    val output: OutputSpec
) {
    val inputAndFormulaColumns: HeaderListing by lazy {
        datasetInfo.headerSuperset().append(formula.headerListing())
    }


    fun toSignature(): ReportRunSignature {
        return ReportRunSignature(
            datasetInfo,
            formula,
            filter.toRunSignature(),
            analysis.toRunSignature()
        )
    }
}