package tech.kzen.auto.server.objects.report.filter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.server.objects.report.pivot.store.BufferedOffsetStore
import tech.kzen.auto.server.objects.report.pivot.store.FileOffsetStore
import tech.kzen.auto.server.objects.report.pivot.store.StoreUtils
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.nio.channels.Channels
import java.nio.file.Path
import java.time.Instant


class IndexedCsvTable(
    private val header: List<String>,
    dir: Path,
    private val bufferSize: Int = 128
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val tableFile = Path.of("table.csv")
        private val offsetFile = Path.of("index.bin")

//        private val format = CSVFormat.RFC4180
        private val format = CSVFormat.DEFAULT
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
    private val bufferWriter = OutputStreamWriter(buffer, Charsets.UTF_8)
    private val bufferPrinter = CSVPrinter(bufferWriter, format)

    private var previousPosition = 0L
    private var previousModified = Instant.now()


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
    fun outputPath(): Path {
        return tablePath
    }

    fun modified(): Instant {
        return previousModified
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @Synchronized
    fun rowCount(): Long {
        // NB: -1 for header
        return (offsetStore.size() - 1).coerceAtLeast(0) + pending.size
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @Synchronized
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
        previousModified = Instant.now()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun preview(start: Long, count: Int): OutputPreview {
        val builder = mutableListOf<List<String>>()
        traverseWithoutHeader(start, count.toLong()) {
            builder.add(it.toList())
        }
        return OutputPreview(header, builder, start)
    }


    fun traverseWithHeader(visitor: (Iterable<String>) -> Unit) {
        visitor.invoke(header)
        traverseWithoutHeader(0, rowCount()) {
            visitor.invoke(it)
        }
    }


    private fun traverseWithoutHeader(start: Long, count: Long, visitor: (Iterable<String>) -> Unit) {
        val adjustedStart = start.coerceAtLeast(0L)

        // NB: header is first
        val offsetStoreStart = (adjustedStart + 1)

        if (offsetStoreStart < offsetStore.size()) {
            val storedStartSpan = offsetStore.get(offsetStoreStart)
            val storedEnd = (offsetStoreStart + count).coerceAtMost(offsetStore.size() - 1)
            val storedEndSpan = offsetStore.get(storedEnd)

            seek(storedStartSpan.offset)

            // NB: don't close, because that would also close handle
            val reader = Channels.newReader(handle.channel, Charsets.UTF_8)
//            val reader = Channels.newReader(handle.channel, Charsets.UTF_8)

            val headerFormat = format.withHeader(*header.toTypedArray())
            val parser = CSVParser(reader, headerFormat)

            var remainingCount = count
            for (row in parser) {
                visitor.invoke(row)

                remainingCount--
                if (remainingCount == 0L) {
                    break
                }
            }

            handle.seek(storedEndSpan.endOffset())
            previousPosition = storedEndSpan.endOffset()

            if (remainingCount > 0) {
                for (row in pending) {
                    visitor.invoke(row)

                    remainingCount--
                    if (remainingCount == 0L) {
                        break
                    }
                }
            }
        }
        else {
            // TODO: test
            val pendingStart =
                if (offsetStore.size() == 0L) {
                    adjustedStart.toInt()
                }
                else {
                    (offsetStoreStart - offsetStore.size()).toInt()
                }

            if (pendingStart < pending.size) {
                val pendingEnd = (pendingStart + count).coerceAtMost(pending.size.toLong()).toInt()
                for (i in pendingStart until pendingEnd) {
                    visitor.invoke(pending[i])
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun seek(position: Long) {
        if (previousPosition == position) {
            return
        }

        handle.seek(position)
        previousPosition = position
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @Synchronized
    override fun close() {
        flushPending()
        StoreUtils.flushAndClose(handle)
        offsetStore.close()
    }
}