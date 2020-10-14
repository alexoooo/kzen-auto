package tech.kzen.auto.server.objects.process.pivot.stats

import tech.kzen.auto.common.objects.document.process.PivotValueType


class MapValueStatistics(
    valueColumnCount: Int
):
    ValueStatistics
{
    private val columns = Array(valueColumnCount) { MapColumnStatistics() }


    override fun add(rowIndex: Long, values: DoubleArray) {
        for (i in columns.indices) {
            val value = values[i]
            if (ValueStatistics.isMissing(value)) {
                continue
            }
            add(rowIndex, i, value)
        }
    }


    override fun get(rowIndex: Long, valueTypes: List<IndexedValue<PivotValueType>>): DoubleArray {
        check(valueTypes.size == columns.size)

        val values = DoubleArray(valueTypes.size)
        for (i in columns.indices) {
            values[i] = get(rowIndex, valueTypes[i].index, valueTypes[i].value)
        }

        return values
    }


    fun add(rowIndex: Long, columnIndex: Int, value: Double) {
        columns[columnIndex].add(rowIndex, value)
    }


    fun get(rowIndex: Long, columnIndex: Int, valueType: PivotValueType): Double {
        return columns[columnIndex].get(rowIndex, valueType)
    }


    override fun close() {}
}