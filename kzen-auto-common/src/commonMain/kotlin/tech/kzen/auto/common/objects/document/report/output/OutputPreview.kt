package tech.kzen.auto.common.objects.document.report.output

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


data class OutputPreview(
    val header: HeaderListing,
    val rows: List<List<String>>,
    val startRow: Long
) {
    companion object {
        const val defaultRowCount = 100
//        const val defaultRowCount = 1_000

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
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            headerKey to header.values,
            rowsKey to rows,
            startKey to startRow.toString())
    }
}