package tech.kzen.auto.common.paradigm.reactive


data class StatisticValueSummary(
    val count: Long,
    val sum: Double,
    val min: Double,
    val max: Double
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val countKey = "count"
        private const val sumKey = "sum"
        private const val minKey = "min"
        private const val maxKey = "max"


        val empty = StatisticValueSummary(
            0,
            0.0,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY)


        fun fromCollection(collection: Map<String, Any>): StatisticValueSummary {
            return StatisticValueSummary(
                collection[countKey] as Long,
                collection[sumKey] as Double,
                collection[minKey] as Double,
                collection[maxKey] as Double)
        }


        fun fromCsv(csv: List<List<String>>): StatisticValueSummary {
            return StatisticValueSummary(
                csv[1][1].toLong(),
                csv[2][1].toDouble(),
                csv[3][1].toDouble(),
                csv[4][1].toDouble())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return count == 0L
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            countKey to count,
            sumKey to sum,
            minKey to min,
            maxKey to max)
    }


    fun toCsv(): List<List<String>> {
        val builder = mutableListOf<List<String>>()

        builder.add(listOf("Statistic", "Value"))
        builder.add(listOf(countKey, count.toString()))
        builder.add(listOf(sumKey, sum.toString()))
        builder.add(listOf(minKey, min.toString()))
        builder.add(listOf(maxKey, max.toString()))

        return builder
    }
}