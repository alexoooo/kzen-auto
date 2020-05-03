package tech.kzen.auto.common.paradigm.reactive


data class NumericValueSummary(
    val density: Map<ClosedFloatingPointRange<Double>, Long>
) {
    companion object {
        val empty = NumericValueSummary(mapOf())

        fun fromCollection(collection: List<List<Any>>): NumericValueSummary {
            return NumericValueSummary(
                collection.map {
                    val from = it[0] as Double
                    val to = it[1] as Double
                    val count = (it[2] as String).toLong()
                    from.rangeTo(to) to count
                }.toMap())
        }
    }


    fun toCollection(): List<List<Any>> {
        return density.entries.map {
            listOf(it.key.start, it.key.endInclusive, it.value.toString())
        }
    }
}