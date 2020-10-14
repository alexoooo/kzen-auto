package tech.kzen.auto.server.objects.process.pivot.row.value.store

import com.google.common.primitives.Longs
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path


class FileIndexedStoreOffset(
    file: Path
):
    IndexedStoreOffset
{
    //-----------------------------------------------------------------------------------------------------------------
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

    private var previousOffset = 0L
    private var endOffset = 0L
    private val readBuffer = ByteArray(Long.SIZE_BYTES * 2)


    //-----------------------------------------------------------------------------------------------------------------
    override fun endOffset(): Long {
        return endOffset
//        return get(fileSize / Long.SIZE_BYTES).endOffset()
    }


    override fun get(index: Long): IndexedStoreOffset.Span {
        val previousEnd: Long
        val endOffset: Long

        if (index == 0L) {
            previousEnd = 0L
            seek(index * Long.SIZE_BYTES)
            endOffset = handle.readLong()
            previousOffset += Long.SIZE_BYTES
        }
        else {
            seek((index - 1) * Long.SIZE_BYTES)
            handle.read(readBuffer)
            previousOffset += readBuffer.size
            previousEnd = Longs.fromBytes(
                readBuffer[0], readBuffer[1], readBuffer[2], readBuffer[3],
                readBuffer[4], readBuffer[5], readBuffer[6], readBuffer[7])
            endOffset = Longs.fromBytes(
                readBuffer[8], readBuffer[9], readBuffer[10], readBuffer[11],
                readBuffer[12], readBuffer[13], readBuffer[14], readBuffer[15])
        }

        val length = (endOffset - previousEnd).toInt()

        return IndexedStoreOffset.Span(
            previousEnd, length)
    }


    override fun add(length: Int) {
        seek(fileSize)

        endOffset += length
        handle.writeLong(endOffset)

        fileSize += Long.SIZE_BYTES
        previousOffset = fileSize
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun seek(offset: Long) {
        if (previousOffset == offset) {
            return
        }

        handle.seek(offset)
        previousOffset = offset
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        handle.close()
    }
}