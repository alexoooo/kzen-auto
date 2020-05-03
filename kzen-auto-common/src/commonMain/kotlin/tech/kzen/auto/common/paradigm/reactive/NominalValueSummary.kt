package tech.kzen.auto.common.paradigm.reactive

import tech.kzen.auto.common.util.DataFormatUtils


data class NominalValueSummary(
    val histogram: Map<String, Long>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = NominalValueSummary(mapOf())


        fun fromCollection(collection: Map<String, String>): NominalValueSummary {
            return NominalValueSummary(collection.mapValues { it.value.toLong() })
        }


        fun fromCsv(csv: List<List<String>>): NominalValueSummary {
            val afterHeader = csv.subList(1, csv.size)
            val histogram = afterHeader
                .map { it[0] to it[1].toLong() }
                .toMap()
            return NominalValueSummary(histogram)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return histogram.isEmpty()
    }


    fun toCollection(): Map<String, String> {
        return histogram.mapValues { it.value.toString() }
    }


    fun toCsv(): List<List<String>> {
        val builder = mutableListOf<List<String>>()

        builder.add(listOf("Value", "Count"))

        for (e in histogram) {
            val encodedValue = DataFormatUtils.csvEncode(e.key)
            builder.add(listOf(e.key, e.value.toString()))
        }

        return builder
    }
}
