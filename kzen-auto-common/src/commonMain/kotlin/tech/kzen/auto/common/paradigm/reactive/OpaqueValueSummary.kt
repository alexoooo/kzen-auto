package tech.kzen.auto.common.paradigm.reactive


data class OpaqueValueSummary(
    val sample: Set<String>
) {
    companion object {
        val empty = OpaqueValueSummary(setOf())

        fun fromCollection(collection: List<String>): OpaqueValueSummary {
            return OpaqueValueSummary(collection.toSet())
        }
    }


    fun toCollection(): List<String> {
        return sample.toList()
    }
}