package tech.kzen.auto.common.objects.document.report.summary


data class TableSummary(
    val columnSummaries: Map<String, ColumnSummary>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = TableSummary(mapOf())


        fun fromCollection(collection: Map<String, Map<String, Any>>): TableSummary {
            return TableSummary(
                collection.mapValues { ColumnSummary.fromCollection(it.value) })
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return columnSummaries.isEmpty() ||
                columnSummaries.values.all { it.isEmpty() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Map<String, Any>> {
        return columnSummaries.mapValues { it.value.toCollection() }
    }
}