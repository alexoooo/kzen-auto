package tech.kzen.auto.server.objects.report.pipeline.output.pivot.stats.map

import tech.kzen.auto.common.objects.document.report.spec.PivotValueType
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.stats.ValueStatistics


class MapValueStatistics(
    valueColumnCount: Int
):
    ValueStatistics
{
    private val columns = Array(valueColumnCount) { MapColumnStatistics() }


    override fun addOrUpdate(rowOrdinal: Long, values: DoubleArray) {
        for (i in columns.indices) {
            val value = values[i]
            if (ValueStatistics.isMissing(value)) {
                continue
            }
            addOrUpdate(rowOrdinal, i, value)
        }
    }


    override fun get(rowOrdinal: Long, valueTypes: List<IndexedValue<PivotValueType>>): DoubleArray {
        val values = DoubleArray(valueTypes.size)
        for (i in valueTypes.indices) {
            values[i] = get(rowOrdinal, valueTypes[i].index, valueTypes[i].value)
        }
        return values
    }


    fun contains(rowOrdinal: Long): Boolean {
        for (column in columns) {
            if (column.contains(rowOrdinal)) {
                return true
            }
        }
        return false
    }


    fun addOrUpdate(rowOrdinal: Long, columnOrdinal: Int, value: Double) {
        columns[columnOrdinal].addOrUpdate(rowOrdinal, value)
    }


    fun get(rowOrdinal: Long, columnOrdinal: Int, valueType: PivotValueType): Double {
        return columns[columnOrdinal].get(rowOrdinal, valueType)
    }


    override fun close() {}
}