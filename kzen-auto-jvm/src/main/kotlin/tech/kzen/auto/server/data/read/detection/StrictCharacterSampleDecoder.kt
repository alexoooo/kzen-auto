package tech.kzen.auto.server.data.read.detection

import tech.kzen.auto.plugin.api.data.StrictCharacterView
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction


object StrictCharacterSampleDecoder {
    fun decode(
        sample: ByteArray,
        endOfInput: Boolean,
        explicitEncoding: String?,
        allowWindows1252: Boolean
    ): DecodedDetectionSample {
        val bom = Bom.detect(sample)
        if (explicitEncoding != null) {
            val configured = charset(explicitEncoding)
            val resolved = bom?.let {
                require(it.compatibleWith(configured)) {
                    "BOM ${it.charset.name()} conflicts with explicit encoding ${configured.name()}"
                }
                it.charset
            } ?: configured.also {
                require(!it.name().equals("UTF-16", ignoreCase = true)) {
                    "Generic UTF-16 requires a BOM"
                }
            }
            return decoded(sample, bom?.bytes?.size ?: 0, resolved, endOfInput, null)
        }
        if (bom != null) {
            return decoded(sample, bom.bytes.size, bom.charset, endOfInput, null)
        }

        try {
            return decoded(sample, 0, Charsets.UTF_8, endOfInput, null)
        }
        catch (utf8Failure: CharacterCodingException) {
            if (!allowWindows1252) {
                throw FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Resolution,
                    "Input is not valid UTF-8; choose an explicit encoding",
                    utf8Failure)
            }
            return try {
                decoded(
                    sample,
                    0,
                    charset("windows-1252"),
                    endOfInput,
                    "Windows-1252 was inferred from the text hint; verify the encoding")
            }
            catch (legacyFailure: CharacterCodingException) {
                throw FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Resolution,
                    "Input is not valid UTF-8 or Windows-1252; choose an explicit encoding",
                    legacyFailure)
            }
        }
    }


    private fun decoded(
        sample: ByteArray,
        offset: Int,
        charset: Charset,
        endOfInput: Boolean,
        warning: String?
    ): DecodedDetectionSample {
        val bytes = sample.copyOfRange(offset, sample.size)
        val text = try {
            decodeStrict(bytes, charset)
        }
        catch (failure: CharacterCodingException) {
            val trim = if (endOfInput) 0 else incompleteSuffix(bytes, charset)
            if (trim == 0) throw failure
            decodeStrict(bytes.copyOf(bytes.size - trim), charset)
        }
        requireTextSafe(text)
        return DecodedDetectionSample(listOf(StrictCharacterView(charset.name(), text)), warning)
    }


    private fun decodeStrict(bytes: ByteArray, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }


    private fun incompleteSuffix(bytes: ByteArray, charset: Charset): Int {
        if (charset == Charsets.UTF_16LE || charset == Charsets.UTF_16BE) return bytes.size and 1
        if (charset != Charsets.UTF_8 || bytes.isEmpty()) return 0
        var start = bytes.lastIndex
        while (start > 0 && bytes[start].toInt() and 0xc0 == 0x80 && bytes.size - start < 4) start--
        val lead = bytes[start].toInt() and 0xff
        val expected = when {
            lead in 0xc2..0xdf -> 2
            lead in 0xe0..0xef -> 3
            lead in 0xf0..0xf4 -> 4
            else -> return 0
        }
        val actual = bytes.size - start
        if (actual >= expected) return 0
        if ((start + 1 until bytes.size).any { bytes[it].toInt() and 0xc0 != 0x80 }) return 0
        return actual
    }


    private fun requireTextSafe(text: String) {
        val codePoints = text.codePoints().iterator()
        while (codePoints.hasNext()) {
            val codePoint = codePoints.nextInt()
            if (codePoint == 0) {
                throw FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Resolution,
                    "Input contains NUL and appears to be binary")
            }
            if (Character.getType(codePoint) == Character.CONTROL.toInt() &&
                codePoint !in setOf('\t'.code, '\n'.code, '\r'.code, '\u000c'.code)) {
                throw FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Resolution,
                    "Input contains a disallowed control character and appears to be binary")
            }
        }
    }


    private fun charset(name: String): Charset = try {
        Charset.forName(name)
    }
    catch (failure: IllegalArgumentException) {
        throw FormatDetectionException(
            tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Resolution,
            "Unsupported character encoding '$name'",
            failure)
    }


    private enum class Bom(val bytes: ByteArray, val charset: Charset) {
        Utf8(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()), Charsets.UTF_8),
        Utf16BigEndian(byteArrayOf(0xfe.toByte(), 0xff.toByte()), Charsets.UTF_16BE),
        Utf16LittleEndian(byteArrayOf(0xff.toByte(), 0xfe.toByte()), Charsets.UTF_16LE);

        fun compatibleWith(configured: Charset): Boolean =
            configured.name().equals("UTF-16", ignoreCase = true) || configured == charset

        companion object {
            fun detect(bytes: ByteArray): Bom? = entries.firstOrNull { bom ->
                bytes.size >= bom.bytes.size && bom.bytes.indices.all { bytes[it] == bom.bytes[it] }
            }
        }
    }
}
