package tech.kzen.auto.common.objects.document.report.summary


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


        fun ofCollection(collection: Map<String, Any>): StatisticValueSummary {
            return StatisticValueSummary(
                collection[countKey] as Long,
                collection[sumKey] as Double,
                collection[minKey] as Double,
                collection[maxKey] as Double)
        }


        fun ofCsv(csv: List<List<String>>): StatisticValueSummary {
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


    fun asCollection(): Map<String, Any> {
        return mapOf(
            countKey to count,
            sumKey to sum,
            minKey to min,
            maxKey to max)
    }


    fun asCsv(): List<List<String>> {
        val builder = mutableListOf<List<String>>()

        builder.add(listOf("Statistic", "Value"))
        builder.add(listOf(countKey, count.toString()))

        // TODO: figure out decimal formatting ala ColumnValueUtils.formatDecimal
        builder.add(listOf(sumKey, sum.toString()))
        builder.add(listOf(minKey, min.toString()))
        builder.add(listOf(maxKey, max.toString()))

        return builder
    }
}