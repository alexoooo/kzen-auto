package tech.kzen.auto.server.objects.process.pivot.stats

import tech.kzen.auto.common.objects.document.process.PivotValueType


/**
 * see: java.util.DoubleSummaryStatistics
 */
data class MutableStatistics(
    private var count: Long = 0,
    private var sum: Double = 0.0,
    private var sumCompensation: Double = 0.0,
    private var min: Double = Double.POSITIVE_INFINITY,
    private var max: Double = Double.NEGATIVE_INFINITY
) {
    fun accept(value: Double) {
        ++count
//        simpleSum += value
        sumWithCompensation(value)
        min = min.coerceAtMost(value)
        max = max.coerceAtLeast(value)
    }


    fun get(valueType: PivotValueType): Double {
        return when (valueType) {
            PivotValueType.Count -> getCount().toDouble()
            PivotValueType.Sum -> getSum()
            PivotValueType.Average -> getAverage()
            PivotValueType.Min -> getMin()
            PivotValueType.Max -> getMax()
        }
    }


    fun getCount(): Long {
        return count
    }


    fun getMin(): Double {
        return min
    }


    fun getMax(): Double {
        return max
    }


    fun getSum(): Double {
        // Better error bounds to add both terms as the final sum
        return sum + sumCompensation
    }


    fun getAverage(): Double {
        return if (getCount() > 0) getSum() / getCount() else 0.0
    }


    private fun sumWithCompensation(value: Double) {
        val tmp = value - sumCompensation
        val velvel = sum + tmp // Little wolf of rounding error
        sumCompensation = velvel - sum - tmp
        sum = velvel
    }
}