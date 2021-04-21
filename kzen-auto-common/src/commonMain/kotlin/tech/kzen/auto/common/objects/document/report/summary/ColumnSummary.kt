package tech.kzen.auto.common.objects.document.report.summary


data class ColumnSummary(
    val count: Long,
    val nominalValueSummary: NominalValueSummary,
    val numericValueSummary: StatisticValueSummary,
    val opaqueValueSummary: OpaqueValueSummary
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = ColumnSummary(
            0,
            NominalValueSummary.empty,
            StatisticValueSummary.empty,
            OpaqueValueSummary.empty)

        private const val countKey = "count"
        private const val nominalKey = "nominal"
        private const val numericKey = "numeric"
        private const val opaqueKey = "opaque"

        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any>): ColumnSummary {
            return ColumnSummary(
                collection[countKey] as Long,
                NominalValueSummary.fromCollection(collection[nominalKey] as Map<String, String>),
                StatisticValueSummary.ofCollection(collection[numericKey] as Map<String, Any>),
                OpaqueValueSummary.fromCollection(collection[opaqueKey] as List<String>)
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return count == 0L &&
                nominalValueSummary.isEmpty() &&
                numericValueSummary.isEmpty() &&
                opaqueValueSummary.isEmpty()

    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any> {
        return mapOf(
            countKey to count,
            nominalKey to nominalValueSummary.toCollection(),
            numericKey to numericValueSummary.asCollection(),
            opaqueKey to opaqueValueSummary.toCollection()
        )
    }
}