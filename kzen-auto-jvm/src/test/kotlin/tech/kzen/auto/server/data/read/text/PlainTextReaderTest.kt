package tech.kzen.auto.server.data.read.text

import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.PlainTextReadConfig
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.format.FormatMaterializationRequest
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.attribute.AttributeSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull


class PlainTextReaderTest {
    @Test
    fun materializationFreezesCanonicalCharactersWithoutClaimingColumnLocking() {
        val config = PlainTextReaderCapability.encode(PlainTextReadConfig(
            CharacterDecodingSpec("utf-8", "permit", "report", "report")))

        val result = PlainTextReaderCapability.materialize(FormatMaterializationRequest(
            "formats.yaml#PlainText",
            ResolvedReadSpec(
                PlainTextReaderCapability.identity,
                listOf(ContentCodingSpec.gzip),
                config),
            observedSchema = null))

        assertEquals(
            "formats.yaml#PlainText",
            result.formatBody.map.getValue(AttributeSegment.ofKey("is")).asString())
        assertEquals(
            "false",
            result.formatBody.map.getValue(AttributeSegment.ofKey("catalogVisible")).asString())
        assertEquals(false, AttributeSegment.ofKey("contentCodings") in result.formatBody.map)
        assertEquals(null, result.schemaBody)
        assertEquals(null, result.editor)
        assertEquals("UTF-8", result.encoding)
        assertEquals(false, PlainTextReaderCapability.supportsColumnLocking)
    }

    @Test
    fun recognizesAllLineEndingsAndPreservesBlankAndFinalLines() {
        val content = StringContent("\nalpha\r\n\rbravo\rtail")
        PlainTextReader(content).use { reader ->
            assertEquals(listOf("", "alpha", "", "bravo", "tail"), generateSequence(reader::read)
                .map(::line).toList())
            assertNull(reader.read())
        }
        assertEquals(1, content.closeCount)
    }

    @Test
    fun emptyInputEmitsNoRowsAndTerminatedInputAddsNoPhantomRow() {
        PlainTextReader(StringContent("")).use { assertNull(it.read()) }
        PlainTextReader(StringContent("value\n")).use {
            assertEquals("value", line(it.read()!!))
            assertNull(it.read())
        }
    }

    private fun line(value: DataValue): String {
        val field = value.access.field(value.root, FieldId("line", 0))
        return value.access.readText(field)
    }

    private class StringContent(private val text: String): SequentialCharacterContent {
        override val resolvedCharsetName = "UTF-8"
        var closeCount = 0
            private set
        private var position = 0

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (position == text.length) return -1
            val count = minOf(length, text.length - position)
            text.toCharArray(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun close() {
            closeCount++
        }
    }
}
