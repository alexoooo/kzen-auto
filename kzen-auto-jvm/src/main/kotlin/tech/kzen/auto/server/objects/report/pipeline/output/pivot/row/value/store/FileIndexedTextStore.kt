package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value.store

import it.unimi.dsi.fastutil.ints.IntArrayList
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTextFlyweight
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.OffsetStore
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.StoreUtils
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path


class FileIndexedTextStore(
    private val file: Path,
    private val offsetStore: OffsetStore
): IndexedTextStore {
    //-----------------------------------------------------------------------------------------------------------------
    private val handle: RandomAccessFile
    private var previousOffset = 0L
    private var readBuffer = ByteArray(64)

    private val writeBufferBytes = ByteArrayOutputStream()
    private val lengthBuffer = IntArrayList()
//    private val writeBuffer = DataOutputStream(writeBufferBytes)


    //-----------------------------------------------------------------------------------------------------------------
    init {
        Files.createDirectories(file.parent)
        handle = RandomAccessFile(file.toFile(), "rw")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun add(text: String) {
        val offset = offsetStore.endOffset()
        seek(offset)
        val length = write(text)
        offsetStore.add(length)
    }

    override fun add(text: RecordTextFlyweight) {
        add(text.toString())
    }


    fun addAll(textValues: List<String>) {
        val offset = offsetStore.endOffset()
        seek(offset)

        for (text in textValues) {
            val encoded = text.encodeToByteArray()
            previousOffset += encoded.size
            writeBufferBytes.write(encoded)
            lengthBuffer.add(encoded.size)
        }

        val writeBites = writeBufferBytes.toByteArray()
        writeBufferBytes.reset()
        handle.write(writeBites)

        offsetStore.addAll(lengthBuffer)
        lengthBuffer.clear()
    }


    override fun get(textOrdinal: Long): String {
        val span = offsetStore.get(textOrdinal)
        seek(span.offset)
        return read(span.length)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun seek(offset: Long) {
        if (previousOffset == offset) {
            return
        }

        handle.seek(offset)
        previousOffset = offset
    }


    private fun read(length: Int): String {
        if (readBuffer.size < length) {
            readBuffer = ByteArray((length * 1.2).toInt())
        }

        val read = handle.read(readBuffer, 0, length)
        check(read == length) { "Unable to read" }
        previousOffset += length
        return String(readBuffer, 0, length, StandardCharsets.UTF_8)
    }


    private fun write(text: String): Int {
        val encoded = text.encodeToByteArray()
        handle.write(encoded)
        previousOffset += encoded.size
        return encoded.size
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        StoreUtils.flushAndClose(handle, file.toString())
        offsetStore.close()
    }
}