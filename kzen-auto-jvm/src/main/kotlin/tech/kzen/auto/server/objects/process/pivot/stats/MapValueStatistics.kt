package tech.kzen.auto.server.objects.process.pivot.stats

import tech.kzen.auto.common.objects.document.process.PivotValueType


class MapValueStatistics(
    valueColumnCount: Int
):
    ValueStatistics
{
    private val columns = Array(valueColumnCount) { MapColumnStatistics() }


    override fun add(rowIndex: Long, columnIndex: Int, value: Double) {
        columns[columnIndex].add(rowIndex, value)
    }


    override fun get(rowIndex: Long, columnIndex: Int, valueType: PivotValueType): Double {
        return columns[columnIndex].get(rowIndex, valueType)
    }

    override fun close() {}
}