package tech.kzen.auto.server.objects.process.pivot.stats

import tech.kzen.auto.common.objects.document.process.PivotValueType


interface ValueStatistics {
    fun add(rowIndex: Long, columnIndex: Int, value: Double)
    fun get(rowIndex: Long, columnIndex: Int, valueType: PivotValueType): Double
}