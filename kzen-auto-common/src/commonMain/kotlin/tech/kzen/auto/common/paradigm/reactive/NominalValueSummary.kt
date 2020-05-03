package tech.kzen.auto.common.paradigm.reactive


data class NominalValueSummary(
    val histogram: Map<String, Long>
) {
    companion object {
        val empty = NominalValueSummary(mapOf())

        fun fromCollection(collection: Map<String, String>): NominalValueSummary {
            return NominalValueSummary(collection.mapValues { it.value.toLong() })
        }
    }


    fun toCollection(): Map<String, String> {
        return histogram.mapValues { it.value.toString() }
    }
}
