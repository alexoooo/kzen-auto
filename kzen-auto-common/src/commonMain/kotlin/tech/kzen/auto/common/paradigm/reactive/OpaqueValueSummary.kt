package tech.kzen.auto.common.paradigm.reactive


data class OpaqueValueSummary(
    val sample: Set<String>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = OpaqueValueSummary(setOf())


        fun fromCollection(collection: List<String>): OpaqueValueSummary {
            return OpaqueValueSummary(collection.toSet())
        }


        fun fromCsv(csv: List<List<String>>): OpaqueValueSummary {
            val afterHeader = csv.subList(1, csv.size)
            val firstColumn = afterHeader.map { it.first() }
            return OpaqueValueSummary(firstColumn.toSet())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return sample.isEmpty()
    }


    fun toCollection(): List<String> {
        return sample.toList()
    }


    fun toCsv(): List<List<String>> {
        val builder = mutableListOf<List<String>>()

        builder.add(listOf("Value"))

        for (value in sample) {
            builder.add(listOf(value))
        }

        return builder
    }
}