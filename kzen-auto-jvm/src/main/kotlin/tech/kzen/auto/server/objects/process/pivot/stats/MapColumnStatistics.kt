package tech.kzen.auto.server.objects.process.pivot.stats

import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import tech.kzen.auto.common.objects.document.process.PivotValueType


class MapColumnStatistics {
    private val stats: Long2ObjectMap<MutableStatistics> = Long2ObjectOpenHashMap()


    fun add(rowIndex: Long, value: Double) {
        val rowStats = stats.getOrPut(rowIndex) { MutableStatistics() }
        rowStats.accept(value)
    }


    fun get(rowIndex: Long, valueType: PivotValueType): Double {
        val rowStats = stats.get(rowIndex)
            ?: return ValueStatistics.missingValue

        return rowStats.get(valueType)
    }
}