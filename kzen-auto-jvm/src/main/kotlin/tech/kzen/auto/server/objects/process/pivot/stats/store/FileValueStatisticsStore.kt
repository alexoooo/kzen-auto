package tech.kzen.auto.server.objects.process.pivot.stats.store

import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongSortedSet
import tech.kzen.auto.common.objects.document.process.PivotValueType
import tech.kzen.auto.server.objects.process.pivot.stats.MutableStatistics
import tech.kzen.auto.server.objects.process.pivot.stats.ValueStatistics
import tech.kzen.auto.server.objects.process.pivot.store.StoreUtils
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path


class FileValueStatisticsStore(
    file: Path,
    valueColumnCount: Int
):
    ValueStatistics,
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val writeBufferLimit = 8 * 1024
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val rowSizeInBytes = valueColumnCount * MutableStatistics.sizeInBytes


    private var fileSize: Long =
        if (! Files.exists(file)) {
            Files.createDirectories(file.parent)
            0
        }
        else {
            Files.size(file)
        }

    private val handle: RandomAccessFile =
        RandomAccessFile(file.toFile(), "rw")

    private val statisticsBuffer = Array(valueColumnCount) { MutableStatistics() }


    private val fileBufferBytes = ByteArray(rowSizeInBytes)
    private val fileBuffer = ByteBuffer.wrap(fileBufferBytes)
    private val writeBuffer = ByteArrayOutputStream()

    private var previousOffset = 0L


    //-----------------------------------------------------------------------------------------------------------------
    fun valueColumnCount(): Int {
        return statisticsBuffer.size
    }


    fun size(): Long {
        return fileSize / rowSizeInBytes
    }


    fun writeAll(modified: LongSortedSet, stats: Long2ObjectMap<Array<MutableStatistics>>) {
        val ordinalBuffer = LongArrayList()
        var previousOrdinal = -1L
        val iterator = modified.iterator()
        while (iterator.hasNext()) {
            val ordinal = iterator.nextLong()
            if (previousOrdinal == -1L || ordinal == previousOrdinal + 1) {
                ordinalBuffer.add(ordinal)
            }
            else {
                writeChunk(ordinalBuffer, stats)
                ordinalBuffer.clear()
                ordinalBuffer.add(ordinal)
            }
            previousOrdinal = ordinal
        }

        writeChunk(ordinalBuffer, stats)
    }


    private fun writeChunk(chunk: LongList, stats: Long2ObjectMap<Array<MutableStatistics>>) {
        if (chunk.isEmpty()) {
            return
        }

        val first = chunk.getLong(0)
        val firstOffset = offset(first)
        fillGap(firstOffset)
        seek(firstOffset)

        var totalSize = 0
        var bufferSize = 0

        val iterator = chunk.iterator()
        while (iterator.hasNext()) {
            val ordinal = iterator.nextLong()
            val rowStats = stats.get(ordinal)

            for (rowStat in rowStats) {
                rowStat.save(fileBuffer)
            }
            fileBuffer.clear()
            writeBuffer.write(fileBufferBytes)

            bufferSize += fileBufferBytes.size
            totalSize += fileBufferBytes.size

            if (bufferSize >= writeBufferLimit) {
                val bytes = writeBuffer.toByteArray()
                writeBuffer.reset()
                handle.write(bytes)
                bufferSize = 0
            }
        }

        if (bufferSize > 0) {
            val bytes = writeBuffer.toByteArray()
            writeBuffer.reset()
            handle.write(bytes)
        }

        fileSize = fileSize.coerceAtLeast(firstOffset + totalSize)
        previousOffset += totalSize
    }


    fun load(rowOrdinal: Long, buffer: Array<MutableStatistics>) {
        val offset = offset(rowOrdinal)

        if (offset >= fileSize) {
            for (stat in buffer) {
                stat.clear()
            }
        }
        else {
            read(offset)

            for (i in statisticsBuffer.indices) {
                buffer[i].copyFrom(statisticsBuffer[i])
            }
        }
    }


    override fun addOrUpdate(rowOrdinal: Long, values: DoubleArray) {
        val offset = offset(rowOrdinal)

        fillGap(offset)

        if (fileSize == offset) {
            append(values)
        }
        else {
            accumulate(offset, values)
        }
    }


    private fun fillGap(offset: Long) {
        if (fileSize >= offset) {
            return
        }

        fileBufferBytes.fill(0)

        seek(fileSize)

        while (fileSize < offset) {
            handle.write(fileBufferBytes)
            fileSize += rowSizeInBytes
        }

        check(fileSize == offset)
        previousOffset = fileSize
    }


    private fun append(values: DoubleArray) {
        for (i in statisticsBuffer.indices) {
            val value = values[i]
            val statistics = statisticsBuffer[i]

            statistics.clear()

            if (! ValueStatistics.isMissing(value)) {
                statistics.accept(value)
            }

            statistics.save(fileBuffer)
        }

        seek(fileSize)
        handle.write(fileBufferBytes)
        fileBuffer.clear()
        fileSize += rowSizeInBytes
        previousOffset = fileSize
    }


    private fun accumulate(offset: Long, values: DoubleArray) {
        read(offset)

        for (i in statisticsBuffer.indices) {
            val value = values[i]
            val statistics = statisticsBuffer[i]

            if (! ValueStatistics.isMissing(value)) {
                statistics.accept(value)
            }

            statistics.save(fileBuffer)
        }

        seek(offset)
        handle.write(fileBufferBytes)
        fileBuffer.clear()
        previousOffset += rowSizeInBytes
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun get(rowOrdinal: Long, valueTypes: List<IndexedValue<PivotValueType>>): DoubleArray {
        val offset = offset(rowOrdinal)

        val values = DoubleArray(valueTypes.size)

        if (offset >= fileSize) {
            for (i in values.indices) {
                values[i] = ValueStatistics.missingValue
            }
        }
        else {
            read(offset)

            for ((i, valueType) in valueTypes.withIndex()) {
                val statistics = statisticsBuffer[valueType.index]

                val value =
                    if (statistics.getCount() == 0L) {
                        ValueStatistics.missingValue
                    }
                    else {
                        statistics.get(valueType.value)
                    }

                values[i] = value
            }
        }

        return values
    }


    private fun read(offset: Long) {
        seek(offset)

        val read = handle.read(fileBufferBytes)
        check(read == rowSizeInBytes)
        previousOffset += rowSizeInBytes

        for (i in statisticsBuffer.indices) {
            statisticsBuffer[i].load(fileBuffer)
        }

        fileBuffer.clear()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun seek(offset: Long) {
        if (previousOffset == offset) {
            return
        }
        handle.seek(offset)
        previousOffset = offset
    }


    private fun offset(rowOrdinal: Long): Long {
        return rowOrdinal * rowSizeInBytes
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        StoreUtils.flushAndClose(handle)
    }
}