package tech.kzen.auto.server.objects.process.pivot.stats.store

import tech.kzen.auto.common.objects.document.process.PivotValueType
import tech.kzen.auto.server.objects.process.pivot.stats.MutableStatistics
import tech.kzen.auto.server.objects.process.pivot.stats.ValueStatistics
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path


class FileValueStatisticsStore(
    file: Path,
    valueColumnCount: Int
):
    AutoCloseable
{
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

    private var previousOffset = 0L


    //-----------------------------------------------------------------------------------------------------------------
    fun add(rowOrdinal: Long, values: DoubleArray) {
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
            val statistics = statisticsBuffer[i]
            statistics.clear()
            statistics.accept(values[i])
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
            val statistics = statisticsBuffer[i]
            statistics.accept(values[i])
            statistics.save(fileBuffer)
        }

        seek(offset)
        handle.write(fileBufferBytes)
        fileBuffer.clear()
        previousOffset += rowSizeInBytes
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun get(rowOrdinal: Long, valueTypes: List<IndexedValue<PivotValueType>>): DoubleArray {
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
        handle.close()
    }
}