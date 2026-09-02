package tech.kzen.auto.server.data.content.character

import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.server.data.content.CharacterDecodingException
import tech.kzen.auto.server.data.content.SequentialByteContent
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction


object CharacterDecoder {
    private const val prefixSize = 3


    fun open(
        bytes: SequentialByteContent,
        spec: CharacterDecodingSpec,
        control: ContentReadControl,
        source: String,
        part: String?
    ): SequentialCharacterContent {
        val prefix = readPrefix(bytes, control)
        val bom = UnicodeBom.detect(prefix)
        val charset = resolveCharset(spec, bom, source, part)
        val consumed = if (bom == null) 0 else bom.bytes.size
        val remainingPrefix = prefix.copyOfRange(consumed, prefix.size)
        val decoder = charset.newDecoder()
        decoder.onMalformedInput(errorAction(spec.malformed, source, part, "malformed"))
        decoder.onUnmappableCharacter(errorAction(spec.unmappable, source, part, "unmappable"))
        return DecoderCharacterContent(
            PrefixedSequentialByteContent(remainingPrefix, bytes), decoder, control, source, part, consumed.toLong())
    }


    private fun readPrefix(bytes: SequentialByteContent, control: ContentReadControl): ByteArray {
        val prefix = ByteArray(prefixSize)
        var size = 0
        while (size < prefix.size) {
            control.checkpoint()
            val count = bytes.read(prefix, size, prefix.size - size)
            if (count == -1) break
            size += count
        }
        return prefix.copyOf(size)
    }


    private fun resolveCharset(
        spec: CharacterDecodingSpec,
        bom: UnicodeBom?,
        source: String,
        part: String?
    ): Charset {
        val bomPolicy = spec.bom.lowercase()
        if (bomPolicy == "forbid" && bom != null) {
            throw CharacterDecodingException(source, part, 0, "BOM is forbidden")
        }
        if ((bomPolicy == "require" || bomPolicy == "detect") && bom == null) {
            throw CharacterDecodingException(source, part, 0, "A supported BOM is required")
        }

        if (bomPolicy !in setOf("detect", "permit", "require", "forbid")) {
            throw CharacterDecodingException(source, part, 0, "Unsupported BOM policy '${spec.bom}'")
        }
        val configured = spec.charset.takeUnless { it.equals("auto", ignoreCase = true) }?.let {
            try {
                Charset.forName(it)
            }
            catch (e: IllegalArgumentException) {
                throw CharacterDecodingException(source, part, 0, "Unsupported charset '$it'", e)
            }
        }
        if (bom != null) {
            if (configured != null && !bom.compatibleWith(configured)) {
                throw CharacterDecodingException(
                    source, part, 0,
                    "BOM ${bom.charset.name()} conflicts with charset ${configured.name()}")
            }
            return bom.charset
        }
        if (configured == null) {
            throw CharacterDecodingException(source, part, 0, "Charset is required when no BOM is present")
        }
        if (configured.name().equals("UTF-16", ignoreCase = true)) {
            throw CharacterDecodingException(source, part, 0, "Generic UTF-16 requires a BOM")
        }
        return configured
    }


    private fun errorAction(value: String, source: String, part: String?, kind: String): CodingErrorAction {
        return when (value.lowercase()) {
            "report" -> CodingErrorAction.REPORT
            "replace" -> CodingErrorAction.REPLACE
            else -> throw CharacterDecodingException(source, part, 0, "Unsupported $kind policy '$value'")
        }
    }


    private enum class UnicodeBom(val bytes: ByteArray, val charset: Charset) {
        Utf8(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()), Charsets.UTF_8),
        Utf16BigEndian(byteArrayOf(0xfe.toByte(), 0xff.toByte()), Charsets.UTF_16BE),
        Utf16LittleEndian(byteArrayOf(0xff.toByte(), 0xfe.toByte()), Charsets.UTF_16LE);

        fun compatibleWith(configured: Charset): Boolean {
            return configured.name().equals("UTF-16", ignoreCase = true) || configured == charset
        }

        companion object {
            fun detect(prefix: ByteArray): UnicodeBom? {
                return entries.firstOrNull { bom ->
                    prefix.size >= bom.bytes.size && bom.bytes.indices.all { prefix[it] == bom.bytes[it] }
                }
            }
        }
    }
}


private class DecoderCharacterContent(
    private val bytes: SequentialByteContent,
    private val decoder: java.nio.charset.CharsetDecoder,
    private val control: ContentReadControl,
    private val source: String,
    private val part: String?,
    initialByteOffset: Long
): SequentialCharacterContent {
    companion object {
        private const val byteBufferSize = 8192
    }

    override val resolvedCharsetName: String = decoder.charset().name()
    override val inspectionRecordLimit: Long = control.inspectionRecordLimit
    override val expandedBytesRead: Long get() = control.expandedBytesRead
    private val input = ByteBuffer.allocate(byteBufferSize).apply { limit(0) }
    private var inputBaseOffset = initialByteOffset
    private var endOfInput = false
    private var flushed = false
    private var closed = false


    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        control.beginOperation()
        try {
            try {
                return readOpen(buffer, offset, length)
            }
            catch (t: Throwable) {
                try {
                    close()
                }
                catch (closeFailure: Throwable) {
                    t.addSuppressed(closeFailure)
                }
                throw t
            }
        }
        finally {
            control.endOperation()
        }
    }


    private fun readOpen(buffer: CharArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        if (length == 0) return 0
        if (flushed) return -1
        val output = CharBuffer.wrap(buffer, offset, length)
        val initialPosition = output.position()

        while (output.hasRemaining()) {
            control.checkpoint()
            val result = decoder.decode(input, output, endOfInput)
            if (result.isError) decodingFailure(result)
            if (result.isOverflow) break
            if (endOfInput) {
                val flushResult = decoder.flush(output)
                if (flushResult.isError) decodingFailure(flushResult)
                if (flushResult.isUnderflow) {
                    flushed = true
                    close()
                }
                break
            }
            refill()
        }

        val count = output.position() - initialPosition
        return if (count == 0 && flushed) -1 else count
    }


    private fun refill() {
        val consumed = input.position()
        input.compact()
        inputBaseOffset += consumed
        val position = input.position()
        val count = bytes.read(input.array(), position, input.remaining())
        if (count == -1) {
            endOfInput = true
        }
        else {
            input.position(position + count)
        }
        input.flip()
    }


    private fun decodingFailure(result: java.nio.charset.CoderResult): Nothing {
        val byteOffset = inputBaseOffset + input.position()
        val cause = try {
            result.throwException()
            null
        }
        catch (e: CharacterCodingException) {
            e
        }
        throw CharacterDecodingException(source, part, byteOffset, "Malformed or unmappable input", cause)
    }


    override fun close() {
        if (closed) return
        closed = true
        bytes.close()
    }
}
