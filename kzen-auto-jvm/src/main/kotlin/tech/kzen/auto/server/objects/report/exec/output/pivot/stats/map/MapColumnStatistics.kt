package tech.kzen.auto.server.objects.report.exec.output.pivot.stats.map

import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import tech.kzen.auto.server.objects.report.exec.output.pivot.stats.MutableStatistics
import tech.kzen.auto.server.objects.report.exec.output.pivot.stats.ValueStatistics


class MapColumnStatistics {
    private val stats: Long2ObjectMap<MutableStatistics> = Long2ObjectOpenHashMap()


    fun contains(rowOrdinal: Long): Boolean {
        return stats.containsKey(rowOrdinal)
    }


    fun addOrUpdate(rowOrdinal: Long, value: Double) {
        val rowStats = stats.getOrPut(rowOrdinal) { MutableStatistics() }
        rowStats.accept(value)
    }


    fun get(rowOrdinal: Long, valueType: PivotValueType): Double {
        val rowStats = stats.get(rowOrdinal)
            ?: return ValueStatistics.missingValue

        return rowStats.get(valueType)
    }
}