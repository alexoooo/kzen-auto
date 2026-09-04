package tech.kzen.auto.server.data.read.text

import tech.kzen.auto.common.data.read.ReadOperationalPolicy
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
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
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
        assertEquals(
            listOf("gzip"),
            (result.formatBody.map.getValue(AttributeSegment.ofKey("contentCodings"))
                    as ListAttributeNotation).values.map { it.asString() })
        assertEquals(null, result.schemaBody)
        assertEquals(null, result.editor)
        assertEquals("UTF-8", result.encoding)
        assertEquals(false, PlainTextReaderCapability.supportsColumnLocking)
    }

    @Test
    fun recognizesAllLineEndingsAndPreservesBlankAndFinalLines() {
        val content = StringContent("\nalpha\r\n\rbravo\rtail")
        PlainTextReader(content, ReadOperationalPolicy()).use { reader ->
            assertEquals(listOf("", "alpha", "", "bravo", "tail"), generateSequence(reader::read)
                .map(::line).toList())
            assertNull(reader.read())
        }
        assertEquals(1, content.closeCount)
    }

    @Test
    fun emptyInputEmitsNoRowsAndTerminatedInputAddsNoPhantomRow() {
        PlainTextReader(StringContent(""), ReadOperationalPolicy()).use { assertNull(it.read()) }
        PlainTextReader(StringContent("value\n"), ReadOperationalPolicy()).use {
            assertEquals("value", line(it.read()!!))
            assertNull(it.read())
        }
    }

    @Test
    fun lineLimitFailureClosesThroughTheCursor() {
        val content = StringContent("abcd")
        val reader = PlainTextReader(content, ReadOperationalPolicy(maximumRecordCharacters = 3))
        val cursor = PlainTextDataCursor(reader, DataShape(
            PlainTextReader.contract, ShapeProvenance.Declared, ShapeStability.Stable))
        assertFailsWith<IllegalArgumentException> { cursor.hasNext() }
        assertEquals(1, content.closeCount)
        cursor.close()
        assertEquals(1, content.closeCount)
    }

    private fun line(value: DataValue): String {
        val field = value.access.field(value.root, FieldId("line", 0))
        return value.access.readText(field)
    }

    private class StringContent(private val text: String): SequentialCharacterContent {
        override val resolvedCharsetName = "UTF-8"
        override val inspectionRecordLimit = Long.MAX_VALUE
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
