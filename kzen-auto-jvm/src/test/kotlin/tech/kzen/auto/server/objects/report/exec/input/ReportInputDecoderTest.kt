package tech.kzen.auto.server.objects.report.exec.input

import org.junit.Test
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.server.objects.report.exec.input.stages.ProcessorInputDecoder
import kotlin.test.assertEquals


class ReportInputDecoderTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun singleAscii() {
        val text = "a"
        val decoded = decode(text.encodeToByteArray())
        assertEquals(text, decoded)
    }


    @Test
    fun singleAsciiAll() {
        val text = "fooooo"
        val decoded = decode(text.encodeToByteArray())
        assertEquals(text, decoded)
    }


    @Test
    fun singleAsciiByOne() {
        val text = "fooooo"
        val decoded = decode(text.encodeToByteArray(), 4)
        assertEquals(text, decoded)
    }


    @Test
    fun singleMultiAll() {
        val text = "€"
        val decoded = decode(text.encodeToByteArray())
        assertEquals(text, decoded)
    }


    @Test
    fun singleMultiSplit() {
        val text = "aaa€a"
        assertEquals(text, decode(text.encodeToByteArray(), 4))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun decode(encoded: ByteArray, bufferSize: Int = encoded.size): String {
        val buffer = DataBlockBuffer.ofText(
            bufferSize.coerceAtLeast(DataBlockBuffer.maxUnicodeSize))
        val decoder = ProcessorInputDecoder(Charsets.UTF_8)
        val builder = StringBuilder()

        var offset = 0
        while (offset < encoded.size) {
            val end = (offset + bufferSize).coerceAtMost(encoded.size)

            buffer.endOfData = end == encoded.size
            encoded.copyInto(buffer.bytes, 0, offset, end)
            buffer.bytesLength = end - offset

            decoder.decode(buffer)
            builder.append(buffer.chars, 0, buffer.charsLength)

            offset = end
        }

        return builder.toString()
    }
}