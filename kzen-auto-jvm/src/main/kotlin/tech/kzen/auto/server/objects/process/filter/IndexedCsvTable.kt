package tech.kzen.auto.server.objects.process.filter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import tech.kzen.auto.common.objects.document.process.OutputPreview
import tech.kzen.auto.server.objects.process.pivot.store.BufferedOffsetStore
import tech.kzen.auto.server.objects.process.pivot.store.FileOffsetStore
import tech.kzen.auto.server.objects.process.pivot.store.StoreUtils
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.nio.channels.Channels
import java.nio.file.Path


class IndexedCsvTable(
    private val header: List<String>,
    dir: Path,
    private val bufferSize: Int = 128
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val tableFile = Path.of("table.csv")
        private val offsetFile = Path.of("index.bin")

        private val format = CSVFormat.RFC4180
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private val headerIndex = header
//        .withIndex()
//        .map { it.value to it.index }
//        .toMap()

    private val offsetStore = BufferedOffsetStore(
        FileOffsetStore(dir.resolve(offsetFile)))


    private val tablePath = dir.resolve(tableFile)

    private val handle: RandomAccessFile =
        RandomAccessFile(tablePath.toFile(), "rw")

    private val pending = mutableListOf<Iterable<String>>()

    private val buffer = OutputStreamBuffer()
    private val bufferWriter = OutputStreamWriter(buffer)
    private val bufferPrinter = CSVPrinter(bufferWriter, format)

    private var previousPosition = 0L


    //-----------------------------------------------------------------------------------------------------------------
    init {
        if (offsetStore.size() == 0L) {
            bufferPrinter.printRecord(header)
            bufferPrinter.flush()

            val length = buffer.flushTo(handle)
            StoreUtils.flush(handle)

            offsetStore.add(length)
            previousPosition = length.toLong()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun add(row: Iterable<String>) {
        pending.add(row)

        if (pending.size == bufferSize) {
            flushPending()
        }
    }


    private fun flushPending() {
        if (pending.isEmpty()) {
            return
        }

        val startOffset = offsetStore.endOffset()

        check(buffer.size() == 0)
        var previousSize = buffer.size()

        for (row in pending) {
            bufferPrinter.printRecord(row)
            bufferPrinter.flush()

            val length = buffer.size() - previousSize
            previousSize = buffer.size()

            offsetStore.add(length)
        }
        pending.clear()

        seek(startOffset)

        val totalLength = buffer.flushTo(handle)
        StoreUtils.flush(handle)

        previousPosition += totalLength
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun preview(start: Long, count: Int): OutputPreview {
        val builder = mutableListOf<List<String>>()
        traverse(start, count) {
            builder.add(it.toList())
        }
        return OutputPreview(header, builder)
    }


    @Synchronized
    fun traverse(start: Long, count: Int, visitor: (Iterable<String>) -> Unit) {
        // NB: header is first
        val adjustedStart = start + 1

        if (adjustedStart < offsetStore.size()) {
            val storedStartSpan = offsetStore.get(adjustedStart)
            val storedEnd = (adjustedStart + count).coerceAtMost(offsetStore.size() - 1)
            val storedEndSpan = offsetStore.get(storedEnd)

            seek(storedStartSpan.offset)

            // NB: don't close, because that would also close handle
            val reader = Channels.newReader(handle.channel, Charsets.UTF_8)

            val headerFormat = format.withHeader(*header.toTypedArray())
            val parser = CSVParser(reader, headerFormat)

            var remainingCount = count
            for (row in parser) {
                visitor.invoke(row)

                remainingCount--
                if (remainingCount == 0) {
                    break
                }
            }

            handle.seek(storedEndSpan.endOffset())
            previousPosition = storedEndSpan.endOffset()

            if (remainingCount > 0) {
                for (row in pending) {
                    visitor.invoke(row)

                    remainingCount--
                    if (remainingCount == 0) {
                        break
                    }
                }
            }
        }
        else {
            val pendingStart = (adjustedStart - offsetStore.size()).toInt()
            if (pendingStart < pending.size) {
                val pendingEnd = (pendingStart + count).coerceAtMost(pending.size)
                for (i in pendingStart until pendingEnd) {
                    visitor.invoke(pending[i])
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun seek(offset: Long) {
        if (previousPosition == offset) {
            return
        }

        handle.seek(offset)
        previousPosition = offset
    }


    @Synchronized
    override fun close() {
        flushPending()
        StoreUtils.flushAndClose(handle)
        offsetStore.close()
    }
}