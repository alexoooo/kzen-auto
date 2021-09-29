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
            filteredColumns: HeaderListing,
            analysisSpec: AnalysisSpec
        ): HeaderListing {
            return when (analysisSpec.type) {
                AnalysisType.PivotTable ->
                    OutputPivotExportSignature.of(analysisSpec.pivot).header

                else -> filteredColumns
            }
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            headerKey to header.values,
            rowsKey to rows,
            startKey to startRow.toString())
    }
}