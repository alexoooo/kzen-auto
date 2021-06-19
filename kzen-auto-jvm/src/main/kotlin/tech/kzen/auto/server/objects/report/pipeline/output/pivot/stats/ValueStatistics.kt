package tech.kzen.auto.server.objects.report.pipeline.output.pivot.stats

import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import java.lang.Double.doubleToRawLongBits


interface ValueStatistics: AutoCloseable {
    companion object {
        const val missingValue = -0.0
        private val missingValueBits = doubleToRawLongBits(missingValue)


        fun isMissing(value: Double): Boolean {
            return doubleToRawLongBits(value) == missingValueBits
        }


        /**
         * Converts -0.0 to 0.0, to be able to use -0.0 as a sentinel
         */
        fun normalize(value: Double): Double {
            return value + 0.0
        }
    }


    fun addOrUpdate(rowOrdinal: Long, values: DoubleArray)

    fun get(rowOrdinal: Long, valueTypes: List<IndexedValue<PivotValueType>>): DoubleArray

//    fun modified(): Instant
}