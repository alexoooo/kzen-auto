package tech.kzen.auto.server.data.read.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull


class StrictCharacterSampleDecoderTest {
    @Test
    fun bomSelectsEncodingAndMustAgreeWithExplicitChoice() {
        val utf16 = byteArrayOf(0xff.toByte(), 0xfe.toByte()) +
            "value".toByteArray(Charsets.UTF_16LE)
        val decoded = StrictCharacterSampleDecoder.decode(utf16, true, null, false)
        assertEquals("UTF-16LE", decoded.characterViews.single().encoding)
        assertEquals("value", decoded.characterViews.single().text)
        assertFailsWith<IllegalArgumentException> {
            StrictCharacterSampleDecoder.decode(utf16, true, "UTF-16BE", false)
        }
    }

    @Test
    fun windowsFallbackIsConstrainedWarnedAndRejectsUndefinedBytes() {
        val decoded = StrictCharacterSampleDecoder.decode(
            byteArrayOf('a'.code.toByte(), 0x80.toByte()), true, null, true)
        assertEquals("a€", decoded.characterViews.single().text)
        assertNotNull(decoded.warning)

        assertFailsWith<FormatDetectionException> {
            StrictCharacterSampleDecoder.decode(byteArrayOf(0x81.toByte()), true, null, true)
        }
        assertFailsWith<FormatDetectionException> {
            StrictCharacterSampleDecoder.decode(byteArrayOf(0xff.toByte()), true, null, false)
        }
    }

    @Test
    fun binaryControlsFailAndBoundaryTruncatedUtf8IsIgnored() {
        assertFailsWith<FormatDetectionException> {
            StrictCharacterSampleDecoder.decode(byteArrayOf('a'.code.toByte(), 0), true, null, false)
        }
        val partial = "ok".encodeToByteArray() + byteArrayOf(0xe2.toByte(), 0x82.toByte())
        val decoded = StrictCharacterSampleDecoder.decode(partial, false, null, false)
        assertEquals("ok", decoded.characterViews.single().text)
    }
}
