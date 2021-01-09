package tech.kzen.auto.server.objects.report.pipeline.output.pivot.store

import com.google.common.primitives.Longs
import it.unimi.dsi.fastutil.ints.IntList
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path


class FileOffsetStore(
    private val file: Path
):
    OffsetStore
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
    private var closed: Boolean = false

    private var endOffset =
        if (fileSize == 0L) {
            0L
        }
        else {
            handle.seek(fileSize - Long.SIZE_BYTES)
            val offset = handle.readLong()
            handle.seek(0)
            offset
        }

    private var previousPosition = 0L
    private val readBuffer = ByteArray(Long.SIZE_BYTES * 2)

    private val writeBufferBytes = ByteArrayOutputStream()
    private val writeBuffer = DataOutputStream(writeBufferBytes)


    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return fileSize / Long.SIZE_BYTES
    }


    override fun endOffset(): Long {
        return endOffset
//        return get(fileSize / Long.SIZE_BYTES).endOffset()
    }


    override fun get(index: Long): OffsetStore.Span {
        val previousEnd: Long
        val endOffset: Long

        if (index == 0L) {
            previousEnd = 0L
            check(fileSize > 0) { "Corrupt file? - ${file.toAbsolutePath().normalize()}" }

            seek(index * Long.SIZE_BYTES)
            endOffset = handle.readLong()
            previousPosition += Long.SIZE_BYTES
        }
        else {
            seek((index - 1) * Long.SIZE_BYTES)
            handle.read(readBuffer)
            previousPosition += readBuffer.size
            previousEnd = Longs.fromBytes(
                readBuffer[0], readBuffer[1], readBuffer[2], readBuffer[3],
                readBuffer[4], readBuffer[5], readBuffer[6], readBuffer[7])
            endOffset = Longs.fromBytes(
                readBuffer[8], readBuffer[9], readBuffer[10], readBuffer[11],
                readBuffer[12], readBuffer[13], readBuffer[14], readBuffer[15])
        }

        val length = (endOffset - previousEnd).toInt()

        return OffsetStore.Span(
            previousEnd, length)
    }


    override fun add(length: Int) {
        seek(fileSize)

        endOffset += length
        handle.writeLong(endOffset)

        fileSize += Long.SIZE_BYTES
        previousPosition = fileSize
    }


    override fun addAll(lengths: IntList) {
        seek(fileSize)

        for (i in 0 until lengths.size) {
            val length = lengths.getInt(i)
            endOffset += length
            writeBuffer.writeLong(endOffset)
        }

        writeBuffer.flush()
        val offsetBytes = writeBufferBytes.toByteArray()
        writeBufferBytes.reset()

        handle.write(offsetBytes)

        fileSize += lengths.size * Long.SIZE_BYTES
        previousPosition = fileSize
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
        if (! closed) {
            StoreUtils.flushAndClose(handle, file.toString())
            closed = true
        }
    }
}