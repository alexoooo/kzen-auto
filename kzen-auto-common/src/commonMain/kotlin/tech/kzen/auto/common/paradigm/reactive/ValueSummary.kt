package tech.kzen.auto.common.paradigm.reactive


data class ValueSummary(
    val count: Long,
    val nominalValueSummary: NominalValueSummary,
    val numericValueSummary: NumericValueSummary,
    val opaqueValueSummary: OpaqueValueSummary
) {
    companion object {
        val empty = ValueSummary(
            0,
            NominalValueSummary.empty,
            NumericValueSummary.empty,
            OpaqueValueSummary.empty)

        private const val countKey = "count"
        private const val nominalKey = "nominal"
        private const val numericKey = "numeric"
        private const val opaqueKey = "opaque"

        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any>): ValueSummary {
            return ValueSummary(
                (collection[countKey] as String).toLong(),
                NominalValueSummary.fromCollection(collection[nominalKey] as Map<String, String>),
                NumericValueSummary.fromCollection(collection[numericKey] as List<List<Any>>),
                OpaqueValueSummary.fromCollection(collection[opaqueKey] as List<String>)
            )
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            countKey to count.toString(),
            nominalKey to nominalValueSummary.toCollection(),
            numericKey to numericValueSummary.toCollection(),
            opaqueKey to opaqueValueSummary.toCollection()
        )
    }
}