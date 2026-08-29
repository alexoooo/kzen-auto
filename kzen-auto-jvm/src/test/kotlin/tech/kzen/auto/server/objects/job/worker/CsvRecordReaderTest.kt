package tech.kzen.auto.server.objects.job.worker

import org.junit.Test
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataNode
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * Unit tests for [CsvRecordReader] — the RFC-4180 parse the [CsvReaderWorker] drives. Validates quoted
 * fields, embedded delimiters / newlines, doubled-quote escapes, `\n` vs `\r\n`, custom delimiters, and the
 * end-of-input edge cases (no spurious trailing empty record).
 */
class CsvRecordReaderTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun parseAll(text: String, delimiter: String = ","): List<List<String>> {
        val records = mutableListOf<List<String>>()
        CsvRecordReader(StringReader(text), delimiter).use { reader ->
            while (true) {
                val record = reader.readRecord() ?: break
                records.add(record.toList())
            }
        }
        return records
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun simpleRows() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("1", "2")),
            parseAll("a,b\n1,2\n"))
    }


    @Test
    fun noTrailingNewline() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("1", "2")),
            parseAll("a,b\n1,2"))
    }


    @Test
    fun emptyFields() {
        assertEquals(
            listOf(listOf("a", "", "b")),
            parseAll("a,,b"))
    }


    @Test
    fun crlfLineEndings() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("1", "2")),
            parseAll("a,b\r\n1,2\r\n"))
    }


    @Test
    fun quotedFieldWithEmbeddedDelimiter() {
        assertEquals(
            listOf(listOf("hello, world", "x")),
            parseAll("\"hello, world\",x"))
    }


    @Test
    fun quotedFieldWithEmbeddedNewline() {
        assertEquals(
            listOf(listOf("line1\nline2", "x")),
            parseAll("\"line1\nline2\",x"))
    }


    @Test
    fun doubledQuoteEscape() {
        // Input: "she said ""hi"""  ->  she said "hi"
        assertEquals(
            listOf(listOf("she said \"hi\"")),
            parseAll("\"she said \"\"hi\"\"\""))
    }


    @Test
    fun customSemicolonDelimiter() {
        assertEquals(
            listOf(listOf("Lviv", "5.1")),
            parseAll("Lviv;5.1", ";"))
    }


    @Test
    fun semicolonDelimiterDoesNotSplitOnComma() {
        assertEquals(
            listOf(listOf("a,b", "c")),
            parseAll("a,b;c", ";"))
    }


    @Test
    fun emptyInputHasNoRecords() {
        assertEquals(listOf(), parseAll(""))
    }


    @Test
    fun readRecordReturnsNullAtEnd() {
        CsvRecordReader(StringReader("only,row"), ",").use { reader ->
            assertEquals(listOf("only", "row"), reader.readRecord()?.toList())
            assertNull(reader.readRecord())
        }
    }


    @Test
    fun emittedRecordRemainsReadableAfterReaderCloses() {
        val reader = CsvRecordReader(StringReader("left,right"), ",")
        val record = requireNotNull(reader.readRecord())
        reader.close()

        JobDataValues.flat(HeaderListing.ofUnique(listOf("first", "second")), record)
        val second = record.field(DataNode(0), FieldId("second"))
        assertEquals("right", record.readText(second))
    }
}
