package tech.kzen.auto.server.objects.process.pivot.row.store

import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path


class FileIndexedTextStore(
    file: Path,
    private val indexedStoreOffset: IndexedStoreOffset
): IndexedTextStore {
    //-----------------------------------------------------------------------------------------------------------------
    private val handle: RandomAccessFile
    private var previousOffset = 0L
    private var readBuffer = ByteArray(64)


    //-----------------------------------------------------------------------------------------------------------------
    init {
        Files.createDirectories(file.parent)
        handle = RandomAccessFile(file.toFile(), "rw")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun add(text: String) {
        val offset = indexedStoreOffset.endOffset()
        seek(offset)
        val length = write(text)
        indexedStoreOffset.add(length)
    }


    override fun get(textIndex: Long): String {
        val span = indexedStoreOffset.get(textIndex)
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
        handle.close()
        indexedStoreOffset.close()
    }
}