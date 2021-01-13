package tech.kzen.auto.server.objects.report.pipeline.output.flat

import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.server.objects.report.pipeline.input.connect.InputStreamFlatData
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.util.ReportInputChain
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.BufferedOffsetStore
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.FileOffsetStore
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.StoreUtils
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path


class IndexedCsvTable(
    private val header: List<String>,
    dir: Path,
    private val bufferSize: Int = 1024 * 1024
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val tableFile: Path = Path.of("table.csv")
        private val offsetFile = Path.of("index.bin")

        private val lineBreak = "\r\n".toCharArray()
//        private val format = CSVFormat.DEFAULT
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val headerIndex = RecordHeaderIndex(header)

    private val offsetStore = BufferedOffsetStore(
        FileOffsetStore(dir.resolve(offsetFile)))

    private val tablePath = dir.resolve(tableFile)

    private var maxPosition =
        if (Files.exists(tablePath)) {
            Files.size(tablePath)
        }
        else {
            0
        }

    private val handle: RandomAccessFile =
        RandomAccessFile(tablePath.toFile(), "rw")

//    private val pending = mutableListOf<Iterable<String>>()
    private val pending = OutputStreamBuffer()
    private val buffer = OutputStreamBuffer()
    private val bufferWriter = OutputStreamWriter(buffer, Charsets.UTF_8)
//    private val bufferPrinter = PrintWriter(bufferWriter)
//    private val bufferPrinter = CSVPrinter(bufferWriter, format)

    private var previousPosition = 0L
//    private var previousModified = Instant.now()


    //-----------------------------------------------------------------------------------------------------------------
    init {
        if (offsetStore.size() == 0L) {
            bufferWriter.write(RecordItemBuffer.of(header).toCsv())
            bufferWriter.write(lineBreak)
            bufferWriter.flush()

            val length = buffer.flushTo(handle)
            StoreUtils.flush(handle, tablePath.toString())

            offsetStore.add(length)

            previousPosition = length.toLong()
            maxPosition = previousPosition
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rowCount(): Long {
        // NB: -1 for header
//        return (offsetStore.size() - 1).coerceAtLeast(0) + pending.size
        return (offsetStore.size() - 1).coerceAtLeast(0)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun add(recordItem: RecordItemBuffer, recordHeader: RecordHeader) {
        val indices = headerIndex.indices(recordHeader)

        var first = true
        for (i in indices) {
            if (first) {
                first = false
            }
            else {
                bufferWriter.write(','.toInt())
            }

            if (i != -1) {
                recordItem.writeCsvField(i, bufferWriter)
            }
        }

        bufferWriter.write(lineBreak)
        bufferWriter.flush()

        val length = buffer.flushTo(pending)
        offsetStore.add(length)

        if (pending.size() >= bufferSize) {
            flushPending()
        }
    }


    private fun flushPending() {
        check(buffer.size() == 0)

        if (pending.size() == 0) {
            return
        }

        seek(maxPosition)
        val totalLength = pending.flushTo(handle)
        StoreUtils.flush(handle, tablePath.toString())

        previousPosition += totalLength
        maxPosition = previousPosition
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun corruptPreview(start: Long): OutputPreview {
        return OutputPreview(header, listOf(), start)
    }


    fun preview(start: Long, count: Int): OutputPreview {
        val builder = mutableListOf<List<String>>()
        traverseWithoutHeader(start, count.toLong()) {
            builder.add(it)
        }
        return OutputPreview(header, builder, start)
    }


    fun traverseWithHeader(visitor: (List<String>) -> Unit) {
        visitor.invoke(header)
        traverseWithoutHeader(0, rowCount()) {
            visitor.invoke(it)
        }
    }


    private fun traverseWithoutHeader(start: Long, count: Long, visitor: (List<String>) -> Unit) {
        if (count <= 0) {
            return
        }

        flushPending()

        val adjustedStart = start.coerceAtLeast(0L)

        // NB: header is first
        val offsetStoreStart = (adjustedStart + 1)

        if (offsetStoreStart < offsetStore.size()) {
            val storedStartSpan = offsetStore.get(offsetStoreStart)
            val storedEnd = (offsetStoreStart + count).coerceAtMost(offsetStore.size() - 1)
            val storedEndSpan = offsetStore.get(storedEnd)

            seek(storedStartSpan.offset)

            // NB: don't close, because that would also close handle
            val inputChain = ReportInputChain(
                InputStreamFlatData.ofCsv(
                    Channels.newInputStream(handle.channel)))

            var remainingCount = storedEnd - offsetStoreStart + 1

            while (true) {
                val hasNext = inputChain.poll { recordItem ->
                    if (--remainingCount > 0) {
                        visitor.invoke(recordItem.toList())
                    }
                }
                if (! hasNext || remainingCount <= 0) {
                    break
                }
            }

            handle.seek(storedEndSpan.endOffset())
            previousPosition = storedEndSpan.endOffset()
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
    override fun close() {
        flushPending()
        StoreUtils.flushAndClose(handle, tablePath.toString())
        offsetStore.close()
    }
}