package tech.kzen.auto.server.data.content.coding

import tech.kzen.auto.server.data.content.ContentCodingException
import tech.kzen.auto.server.data.content.SequentialByteContent
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater


class GzipSequentialByteContent(
    private val inner: SequentialByteContent,
    private val control: ContentReadControl,
    private val source: String,
    private val part: String?
): SequentialByteContent {
    companion object {
        private const val gzipMagicFirst = 0x1f
        private const val gzipMagicSecond = 0x8b
        private const val deflateMethod = 8
        private const val flagHeaderCrc = 0x02
        private const val flagExtra = 0x04
        private const val flagName = 0x08
        private const val flagComment = 0x10
        private const val reservedFlags = 0xe0
        private const val fixedHeaderRemainder = 6
        private const val trailerSize = 8
        private const val inputBufferSize = 8192
    }

    private val inflater = Inflater(true)
    private val checksum = CRC32()
    private val headerChecksum = CRC32()
    private val input = ByteArray(inputBufferSize)
    private var inputLength = 0
    private var tailIndex = 0
    private var initialized = false
    private var complete = false
    private var closed = false
    private var expandedSize = 0L
    private var compressedOffset = 0L


    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        if (length == 0) return 0
        control.checkpoint()
        initialize()
        if (complete) return -1

        while (true) {
            if (inflater.needsInput()) {
                inputLength = inner.read(input)
                if (inputLength == -1) fail("Truncated gzip stream")
                compressedOffset += inputLength
                inflater.setInput(input, 0, inputLength)
            }

            val count = try {
                inflater.inflate(buffer, offset, length)
            }
            catch (e: DataFormatException) {
                fail("Corrupt deflate payload", e)
            }
            control.checkpoint()

            if (count > 0) {
                checksum.update(buffer, offset, count)
                expandedSize += count
                return count
            }
            if (inflater.finished()) {
                tailIndex = inputLength - inflater.remaining
                verifyTrailerAndEnd()
                complete = true
                return -1
            }
            if (inflater.needsDictionary()) fail("Gzip stream requires a preset dictionary")
            if (!inflater.needsInput()) fail("Corrupt gzip stream made no progress")
        }
    }


    private fun initialize() {
        if (initialized) return
        initialized = true
        if (readHeaderByte() != gzipMagicFirst || readHeaderByte() != gzipMagicSecond) {
            fail("Invalid gzip signature")
        }
        if (readHeaderByte() != deflateMethod) fail("Unsupported gzip compression method")
        val flags = readHeaderByte()
        if (flags and reservedFlags != 0) fail("Reserved gzip flags are set")
        repeat(fixedHeaderRemainder) { readHeaderByte() }
        if (flags and flagExtra != 0) {
            val extraLength = readHeaderByte() or (readHeaderByte() shl 8)
            repeat(extraLength) { readHeaderByte() }
        }
        if (flags and flagName != 0) skipZeroTerminated()
        if (flags and flagComment != 0) skipZeroTerminated()
        if (flags and flagHeaderCrc != 0) {
            val expected = readRequiredByte() or (readRequiredByte() shl 8)
            if (expected != (headerChecksum.value.toInt() and 0xffff)) fail("Gzip header checksum mismatch")
        }
    }


    private fun skipZeroTerminated() {
        while (readHeaderByte() != 0) {
            control.checkpoint()
        }
    }


    private fun readHeaderByte(): Int {
        val value = readRequiredByte()
        headerChecksum.update(value)
        return value
    }


    private fun readRequiredByte(): Int {
        val single = ByteArray(1)
        val count = inner.read(single)
        if (count == -1) fail("Truncated gzip header")
        compressedOffset += count
        control.checkpoint()
        return single[0].toInt() and 0xff
    }


    private fun verifyTrailerAndEnd() {
        val trailer = ByteArray(trailerSize)
        for (index in trailer.indices) trailer[index] = readTailByte().toByte()
        val expectedChecksum = littleEndianInt(trailer, 0).toLong() and 0xffff_ffffL
        val expectedSize = littleEndianInt(trailer, 4).toLong() and 0xffff_ffffL
        if (expectedChecksum != checksum.value) fail("Gzip checksum mismatch")
        if (expectedSize != (expandedSize and 0xffff_ffffL)) fail("Gzip expanded size mismatch")
        if (tailIndex < inputLength || inner.read(ByteArray(1)) != -1) {
            fail("Trailing bytes after gzip member")
        }
    }


    private fun readTailByte(): Int {
        if (tailIndex < inputLength) return input[tailIndex++].toInt() and 0xff
        return readRequiredByte()
    }


    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }


    private fun fail(detail: String, cause: Throwable? = null): Nothing {
        throw ContentCodingException(source, part, compressedOffset, detail, cause)
    }


    override fun close() {
        if (closed) return
        closed = true
        inflater.end()
        inner.close()
    }
}
