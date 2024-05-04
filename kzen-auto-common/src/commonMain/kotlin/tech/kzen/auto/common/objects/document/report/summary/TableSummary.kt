package tech.kzen.auto.common.objects.document.report.summary

import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabelMap


data class TableSummary(
    val columnSummaries: HeaderLabelMap<ColumnSummary>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = TableSummary(HeaderLabelMap(mapOf()))


        fun fromCollection(collection: Map<String, Map<String, Any>>): TableSummary {
            return TableSummary(HeaderLabelMap(
                collection
                    .map {
                        HeaderLabel.ofString(it.key) to
                                ColumnSummary.fromCollection(it.value)
                    }
                    .toMap()))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return columnSummaries.map.isEmpty() ||
                columnSummaries.map.values.all { it.isEmpty() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Map<String, Any>> {
        return columnSummaries
            .map
            .map {
                it.key.asString() to it.value.toCollection()
            }
            .toMap()
    }
}