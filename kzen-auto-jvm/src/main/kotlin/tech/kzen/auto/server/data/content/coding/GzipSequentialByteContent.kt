package tech.kzen.auto.server.data.content.coding

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import tech.kzen.auto.server.data.content.ContentCodingException
import tech.kzen.auto.server.data.content.SequentialByteContent
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream


/**
 * Gzip decoding of [inner] by Commons Compress: every member of a multi-member file in turn (a parallel compressor
 * such as the MiGz encoder Write and Report's export use writes many), each member's checksum and size checked,
 * and bytes after the last member that do not start another one an error rather than ignored. A failure names the
 * compressed bytes read so far. The decoder opens on the first read, so a bad header fails there.
 */
class GzipSequentialByteContent(
    private val inner: SequentialByteContent,
    private val control: ContentReadControl,
    private val source: String,
    private val part: String?
): SequentialByteContent {
    companion object {
        private const val inputBufferSize = 64 * 1024
    }

    private var compressedOffset = 0L
    private var decoder: GzipCompressorInputStream? = null
    private var closed = false


    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        if (length == 0) return 0
        control.checkpoint()
        try {
            val gzip = decoder
                ?: GzipCompressorInputStream(BufferedInputStream(CompressedInput(), inputBufferSize), true)
                    .also { decoder = it }
            return gzip.read(buffer, offset, length)
        }
        catch (e: IOException) {
            throw ContentCodingException(source, part, compressedOffset, e.message ?: "Corrupt gzip stream", e)
        }
    }


    override fun close() {
        if (closed) return
        closed = true
        decoder?.close() ?: inner.close()
    }


    /** [inner] as the decoder's input, counting its bytes and checking for cancellation between reads. */
    private inner class CompressedInput: InputStream() {
        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) == -1) -1 else single[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            control.checkpoint()
            val count = inner.read(buffer, offset, length)
            if (count > 0) compressedOffset += count
            return count
        }

        override fun close() {
            inner.close()
        }
    }
}
