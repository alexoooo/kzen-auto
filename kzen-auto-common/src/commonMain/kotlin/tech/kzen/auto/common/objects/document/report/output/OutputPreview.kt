package tech.kzen.auto.common.objects.document.report.output

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType


data class OutputPreview(
    val renderedHeader: List<String>,
    val rows: List<List<String>>,
    val startRow: Long
) {
    @Suppress("ConstPropertyName")
    companion object {
        private const val headerKey = "header"
        private const val rowsKey = "rows"
        private const val startKey = "start"


        @Suppress("UNCHECKED_CAST")
        fun ofCollection(collection: Map<String, Any>): OutputPreview {
            return OutputPreview(
                collection[headerKey] as List<String>,
                collection[rowsKey] as List<List<String>>,
                (collection[startKey] as String).toLong())
        }


        fun emptyHeaderListing(
            filteredColumns: HeaderListing,
            analysisSpec: AnalysisSpec
        ): List<String> {
            return when (analysisSpec.type) {
                AnalysisType.PivotTable ->
                    OutputPivotExportSignature.of(analysisSpec.pivot).header.map { it.render() }

                else ->
                    filteredColumns.values.map { it.render() }
            }
        }
    }


    fun asCollection(): Map<String, Any> {
        return mapOf(
            headerKey to renderedHeader,
            rowsKey to rows,
            startKey to startRow.toString())
    }
}