package tech.kzen.auto.server.objects.process.pivot.stats

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.LongRBTreeSet
import tech.kzen.auto.common.objects.document.process.PivotValueType
import tech.kzen.auto.server.objects.process.pivot.stats.store.FileValueStatisticsStore


class BufferedValueStatistics(
    private val fileValueStatisticsStore: FileValueStatisticsStore,
    private val size: Int = 4 * 1024
):
    ValueStatistics
{
    //-----------------------------------------------------------------------------------------------------------------
    private val buffer = Long2ObjectLinkedOpenHashMap<Array<MutableStatistics>>()
    private val modifiedOrdinals = LongRBTreeSet()


    //-----------------------------------------------------------------------------------------------------------------
    override fun addOrUpdate(rowOrdinal: Long, values: DoubleArray) {
        val stats = buffer.getAndMoveToLast(rowOrdinal)
        if (stats != null) {
            accept(stats, values)
            modifiedOrdinals.add(rowOrdinal)
        }
        else {
            val newStats =
                Array(fileValueStatisticsStore.valueColumnCount()) { MutableStatistics() }

            if (rowOrdinal < fileValueStatisticsStore.size()) {
                fileValueStatisticsStore.load(rowOrdinal, newStats)
            }

            accept(newStats, values)

            buffer[rowOrdinal] = newStats
            modifiedOrdinals.add(rowOrdinal)

            if (buffer.size == size) {
                val firstOrdinal = buffer.firstLongKey()
                if (modifiedOrdinals.contains(firstOrdinal)) {
                    flush()
                }
                buffer.removeFirst()
            }
        }
    }


    private fun flush() {
        fileValueStatisticsStore.writeAll(modifiedOrdinals, buffer)
        modifiedOrdinals.clear()
    }


    private fun accept(stats: Array<MutableStatistics>, values: DoubleArray) {
        for (i in stats.indices) {
            val value = values[i]
            if (! ValueStatistics.isMissing(value)) {
                stats[i].accept(values[i])
            }
        }
    }


    override fun get(rowOrdinal: Long, valueTypes: List<IndexedValue<PivotValueType>>): DoubleArray {
        val stats = buffer.get(rowOrdinal)
        if (stats != null) {
            return getFromBuffer(stats, valueTypes)
        }

        return fileValueStatisticsStore.get(rowOrdinal, valueTypes)
    }


    private fun getFromBuffer(
        stats: Array<MutableStatistics>,
        valueTypes: List<IndexedValue<PivotValueType>>
    ): DoubleArray {
        val values = DoubleArray(valueTypes.size)

        for ((i, valueType) in valueTypes.withIndex()) {
            val statistics = stats[valueType.index]

            val value =
                if (statistics.getCount() == 0L) {
                    ValueStatistics.missingValue
                }
                else {
                    statistics.get(valueType.value)
                }

            values[i] = value
        }

        return values
    }


    override fun close() {
        fileValueStatisticsStore.close()
    }
}