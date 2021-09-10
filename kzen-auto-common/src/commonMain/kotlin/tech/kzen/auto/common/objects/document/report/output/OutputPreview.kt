package tech.kzen.auto.common.objects.document.report.output

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType


data class OutputPreview(
    val header: HeaderListing,
    val rows: List<List<String>>,
    val startRow: Long
) {
    companion object {
        private const val headerKey = "header"
        private const val rowsKey = "rows"
        private const val startKey = "start"


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any>): OutputPreview {
            return OutputPreview(
                HeaderListing(collection[headerKey] as List<String>),
                collection[rowsKey] as List<List<String>>,
                (collection[startKey] as String).toLong())
        }


        fun emptyHeaderListing(
            inputAndCalculatedColumns: HeaderListing,
            analysisSpec: AnalysisSpec
        ): HeaderListing {
            return when (analysisSpec.type) {
                AnalysisType.PivotTable ->
                    analysisSpec.pivot.rows.append(
                        analysisSpec.pivot.values.headerListing())

                else -> inputAndCalculatedColumns
            }
        }


//        private fun headerNames(reportRunContext: ReportRunContext): HeaderListing {
//            val runSignature = reportRunContext.toSignature()
//
//            return when {
//                runSignature.hasPivot() ->
//                    PivotBuilder.ExportSignature.of(
//                        reportRunContext.analysis.pivot.rows,
//                        reportRunContext.analysis.pivot.values
//                    ).header
//
//                else ->
//                    runSignature.inputAndFormulaColumns
//            }
//        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            headerKey to header.values,
            rowsKey to rows,
            startKey to startRow.toString())
    }
}