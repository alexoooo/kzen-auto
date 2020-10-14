package tech.kzen.auto.server.objects.process.pivot.stats

import tech.kzen.auto.common.objects.document.process.PivotValueType
import tech.kzen.auto.server.objects.process.pivot.stats.store.FileValueStatisticsStore


class StoreValueStatistics(
    private val fileValueStatisticsStore: FileValueStatisticsStore
):
    ValueStatistics
{
    override fun add(rowIndex: Long, values: DoubleArray) {
        fileValueStatisticsStore.add(rowIndex, values)
    }


    override fun get(rowIndex: Long, valueTypes: List<IndexedValue<PivotValueType>>): DoubleArray {
        return fileValueStatisticsStore.get(rowIndex, valueTypes)
    }


    override fun close() {
        fileValueStatisticsStore.close()
    }
}