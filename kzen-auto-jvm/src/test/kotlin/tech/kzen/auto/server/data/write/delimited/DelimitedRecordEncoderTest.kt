package tech.kzen.auto.server.data.write.delimited

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.common.data.schema.RecordSchema
import tech.kzen.auto.plugin.api.data.ReaderByteInput
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.server.data.write.RecordCells
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedFormat
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.auto.server.objects.report.exec.output.export.format.CsvExportFormatter
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class DelimitedRecordEncoderTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val columns = listOf("id", "note, with comma", "#tag")

    private val rows = listOf(
        listOf("plain", "", "x"),
        listOf("", "a,b", "say \"hi\""),
        listOf("semi;colon", "tab\tchar", "pipe|bar"),
        listOf("line\nfeed", "carriage\rreturn", "crlf\r\nboth"),
        listOf("#hash", "trail ", " lead"),
        listOf("it's", "naïve café", "ünïcödé"),
        listOf("", "", ""))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun dialectMatrixRoundTripsThroughTheConfiguredReader() {
        val dialects = listOf(
            Dialect(),
            Dialect(delimiter = ";"),
            Dialect(delimiter = "\t"),
            Dialect(delimiter = "|"),
            Dialect(quote = "'"),
            Dialect(separator = "crlf"),
            Dialect(trimming = "unquoted"),
            Dialect(bom = "require"),
            Dialect(bom = "detect", charset = "UTF-16BE"),
            Dialect(bom = "require", charset = "UTF-16LE", separator = "crlf"),
            Dialect(bom = "forbid", charset = "ISO-8859-1"),
            Dialect(commentPrefix = "#"),
            Dialect(skipLeadingLines = 2),
            Dialect(skipLeadingLines = 1, bom = "require", separator = "crlf"),
            Dialect(skipLeadingLines = 1, header = "absent", schema = textSchema(columns)),
            Dialect(header = "absent", schema = textSchema(columns)),
            Dialect(header = "present", schema = textSchema(columns)),
            Dialect(header = "infer-labels"))

        for (dialect in dialects) {
            val format = dialect.format()
            val read = read(format, write(format, columns, rows))
            assertEquals(rows, read.rows, dialect.toString())
            if (dialect.header == "present") {
                assertEquals(columns, read.names, dialect.toString())
            }
        }
    }


    @Test
    fun withoutAnEscapeQuotedFieldsStillCarryDelimitersAndNewlines() {
        val format = Dialect(escape = "none").format()
        val unquotable = rows.filter { row -> row.none { '"' in it } }

        assertEquals(unquotable, read(format, write(format, columns, unquotable)).rows)
    }


    @Test
    fun withoutAQuoteFieldsFreeOfStructureRoundTrip() {
        val format = Dialect(quote = "").format()
        val names = listOf("id", "note", "tag")
        val plain = listOf(listOf("", "#x", "trail "), listOf("say \"hi\"", "a;b", ""))

        val written = write(format, names, plain)

        assertEquals("id,note,tag\n,#x,trail \nsay \"hi\",a;b,\n", String(written, Charsets.UTF_8))
        assertEquals(plain, read(format, written).rows)
    }


    @Test
    fun nullCellsRoundTripAsTheNullTokenInNullableSchemaColumns() {
        val names = listOf("name", "amount")
        val withNulls = listOf(listOf("alpha", null), listOf(null, "12"), listOf("", "N/A?"))
        for (header in listOf("present", "absent")) {
            val format = Dialect(header = header, nullToken = "NA", schema = textSchema(names, nullable = true))
                .format()

            assertEquals(withNulls, read(format, write(format, names, withNulls)).rows, header)
        }
    }


    @Test
    fun aNullTokenThatNeedsQuotingStillReadsBackAsNull() {
        val names = listOf("name", "amount")
        val withNulls = listOf(listOf("really", null), listOf(null, "#N/A"))
        val format = Dialect(nullToken = "#N/A, really", schema = textSchema(names, nullable = true)).format()

        assertEquals(withNulls, read(format, write(format, names, withNulls)).rows)
    }


    @Test
    fun aLineThatWouldReadAsACommentQuotesItsFirstField() {
        val format = Dialect(commentPrefix = "//").format()
        val names = listOf("//name", "value")
        val commented = listOf(listOf("//x", "y"), listOf("/", "/z"))

        val written = write(format, names, commented)

        assertEquals("\"//name\",value\n\"//x\",y\n/,/z\n", String(written, Charsets.UTF_8))
        val read = read(format, written)
        assertEquals(names, read.names)
        assertEquals(commented, read.rows)
    }


    @Test
    fun aCommentPrefixStartingWithTheQuoteFailsByName() {
        val format = Dialect(commentPrefix = "\"#").format()

        val failure = assertFailsWith<IllegalArgumentException> {
            write(format, listOf("a", "b"), listOf(listOf("#x", "y")))
        }

        assertTrue(failure.message!!.contains("read as a comment"), failure.message)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun valuesThatCannotRoundTripFailByName() {
        val names = listOf("name", "note")
        val nullable = textSchema(names, nullable = true)
        val cases = listOf(
            Refusal(Dialect(escape = "none"), listOf("x", "say \"hi\""), "column 'note' contains the quote"),
            Refusal(Dialect(quote = ""), listOf("a,b", "x"), "column 'name' contains the delimiter"),
            Refusal(Dialect(quote = ""), listOf("x", "two\nlines"), "contains a line feed"),
            Refusal(Dialect(quote = ""), listOf("x", "cr\rhere"), "contains a carriage return"),
            Refusal(Dialect(quote = "", trimming = "unquoted"), listOf(" padded", "x"), "whitespace"),
            Refusal(Dialect(quote = ""), listOf("﻿marked", "x"), "byte-order mark"),
            Refusal(Dialect(quote = "", commentPrefix = "//"), listOf("//x", "y"), "read as a comment"),
            Refusal(Dialect(charset = "ISO-8859-1"), listOf("x", "costs €5"),
                "column 'note' holds '€' (U+20AC), which ISO-8859-1 cannot encode"),
            Refusal(Dialect(), listOf("x", "broken \uD800 pair"), "unpaired surrogate (U+D800)"),
            Refusal(Dialect(), listOf(null, "x"), "column 'name' is null, but the format has no null token"),
            Refusal(Dialect(nullToken = "NA"), listOf(null, "x"), "the format has none"),
            Refusal(Dialect(nullToken = "NA", schema = textSchema(names)), listOf(null, "x"),
                "its schema does not allow null"),
            Refusal(Dialect(nullToken = "NA", schema = nullable), listOf("x", "NA"),
                "column 'note' holds 'NA', the format's null token"))

        for (case in cases) {
            val encoder = ConfiguredDelimitedWriterCapability.encoder(case.dialect.format(), names)
            val output = DataRecordBuffer()
            encoder.encodeRecord(ListCells(listOf("kept", "kept")), output)
            val before = output.bytes.copyOf(output.bytesLength)

            val failure = assertFailsWith<IllegalArgumentException>(case.toString()) {
                encoder.encodeRecord(ListCells(case.row), output)
            }

            assertTrue(failure.message!!.contains(case.expected), "$case: ${failure.message}")
            assertTrue(failure.message!!.startsWith("Dialect: "), failure.message)
            assertContentEquals(before, output.bytes.copyOf(output.bytesLength), case.toString())
        }
    }


    @Test
    fun columnsTheFormatCannotWriteFailWhenTheEncoderIsCreated() {
        val cases = listOf(
            Dialect() to listOf("a", "a") to "repeat column names: a",
            Dialect() to listOf("a", "") to "empty column name",
            Dialect() to emptyList<String>() to "at least one column",
            Dialect(schema = textSchema(listOf("a", "b"))) to listOf("a", "c") to "do not match its schema",
            Dialect(header = "absent", schema = textSchema(listOf("a", "b"))) to listOf("b", "a") to
                "do not match its schema",
            Dialect(bom = "require", charset = "ISO-8859-1") to listOf("a") to
                "byte-order mark cannot be written in ISO-8859-1",
            Dialect(delimiter = "€", charset = "ISO-8859-1") to listOf("a") to "cannot encode the format's own '€'")

        for ((setup, expected) in cases) {
            val (dialect, names) = setup
            val failure = assertFailsWith<IllegalArgumentException>(dialect.toString()) {
                ConfiguredDelimitedWriterCapability.encoder(dialect.format(), names)
            }
            assertTrue(failure.message!!.contains(expected), "$dialect: ${failure.message}")
        }
    }


    @Test
    fun aHeaderNameThatCannotBeWrittenNamesTheColumn() {
        val encoder = ConfiguredDelimitedWriterCapability.encoder(Dialect(escape = "none").format(), listOf("say \"x\""))

        val failure = assertFailsWith<IllegalArgumentException> { encoder.encodeHeader(DataRecordBuffer()) }

        assertTrue(failure.message!!.contains("the name of column 'say \"x\"'"), failure.message)
    }


    @Test
    fun unmappableCharactersAreSubstitutedUnderReplace() {
        val format = Dialect(charset = "ISO-8859-1", unmappable = "replace").format()

        val read = read(format, write(format, listOf("price"), listOf(listOf("€5"))))

        assertEquals(listOf(listOf("?5")), read.rows)
    }


    @Test
    fun recordsAndTheHeaderAppendToTheBytesAlreadyInTheOutput() {
        val encoder = ConfiguredDelimitedWriterCapability.encoder(Dialect(bom = "require").format(), listOf("a"))
        val output = DataRecordBuffer()

        encoder.encodeHeader(output)
        encoder.encodeRecord(ListCells(listOf("x")), output)
        encoder.encodeHeader(output)

        val bom = "﻿".toByteArray(Charsets.UTF_8)
        assertContentEquals(bom + "a\nx\n".toByteArray() + bom + "a\n".toByteArray(),
            output.bytes.copyOf(output.bytesLength))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun configuredCsvWritesWhatReportsCsvExportWrites() {
        val header = listOf(" lead", "name", "#count", "trail ")
        val tricky = listOf(
            listOf("", "a,b", "say \"hi\"", "x"),
            listOf("plain", "", "#lead", "x#"),
            listOf("multi\nline", "cr\rx", "both\r\n", "tab\t"),
            listOf(" lead", "trail ", "\ttab-lead", "ünï € 😀"),
            listOf("", "", "", ""),
            listOf("'single'", "semi;colon", "pipe|bar", "\"\""))
        val expected = ByteArrayOutputStream()
        val formatted = DataRecordBuffer()
        for (record in listOf(header) + tricky) {
            formatted.clear()
            CsvExportFormatter().format(FlatFileRecord.of(record), formatted)
            expected.write(String(formatted.chars, 0, formatted.charsLength).toByteArray(Charsets.UTF_8))
        }

        val written = write(ConfiguredDelimitedTestFormats.csv(), header, tricky)

        assertEquals(expected.toString(Charsets.UTF_8), String(written, Charsets.UTF_8))
        assertContentEquals(expected.toByteArray(), written)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun encodingARecordIntoAReusedBufferAllocatesNothing() {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        if (!threads.isThreadAllocatedMemoryEnabled) {
            threads.isThreadAllocatedMemoryEnabled = true
        }
        val names = listOf("name", "amount", "note", "tag", "empty")
        val sample = arrayOf(
            arrayOf<String?>("alpha", "12.5", "a,b", "#tag", ""),
            arrayOf<String?>("", "say \"hi\"", "line\nfeed", "trail ", null),
            arrayOf<String?>("naïve café", "0", "plain", "x", null))
        val formats = listOf(
            ConfiguredDelimitedTestFormats.csv(),
            Dialect(delimiter = "\t", separator = "crlf", nullToken = "NA", charset = "ISO-8859-1",
                schema = textSchema(names, nullable = true)).format())

        for (format in formats) {
            val encoder = ConfiguredDelimitedWriterCapability.encoder(format, names)
            val output = DataRecordBuffer()
            val cells = ArrayCells()
            val rowsWithoutNulls = if (format.baseConfig.typedDecode.nullToken == null) {
                sample.map { row -> Array<String?>(row.size) { row[it] ?: "" } }.toTypedArray()
            }
            else {
                sample
            }

            val warmupRecords = 50_000
            val measuredRecords = 200_000
            for (i in 0 until warmupRecords) {
                output.clear()
                cells.row = rowsWithoutNulls[i % rowsWithoutNulls.size]
                encoder.encodeRecord(cells, output)
            }
            val before = threads.currentThreadAllocatedBytes
            for (i in 0 until measuredRecords) {
                output.clear()
                cells.row = rowsWithoutNulls[i % rowsWithoutNulls.size]
                encoder.encodeRecord(cells, output)
            }
            val allocated = threads.currentThreadAllocatedBytes - before

            // Anything per record would cost at least one object header (16 bytes) per record
            val budgetBytes = 64 * 1024
            assertTrue(allocated < budgetBytes,
                "${format.title}: $allocated bytes allocated over $measuredRecords records")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class Dialect(
        val delimiter: String = ",",
        val quote: String = "\"",
        val escape: String = "double-quote",
        val separator: String = "lf",
        val trimming: String = "none",
        val header: String = "present",
        val charset: String = "UTF-8",
        val bom: String = "permit",
        val unmappable: String = "report",
        val nullToken: String = "",
        val schema: DataContract? = null,
        val commentPrefix: String = "",
        val skipLeadingLines: Int = 0
    ) {
        fun format(): ConfiguredDelimitedFormat = ConfiguredDelimitedFormat(
            "Dialect",
            listOf("txt"),
            true,
            delimiter,
            quote,
            escape,
            separator,
            trimming,
            header,
            charset,
            bom,
            "report",
            unmappable,
            nullToken,
            schema?.let(::FixedSchema),
            skipLeadingLines = skipLeadingLines,
            commentPrefix = commentPrefix)
    }


    private data class Refusal(
        val dialect: Dialect,
        val row: List<String?>,
        val expected: String)


    private class FixedSchema(private val contract: DataContract): RecordSchema {
        override fun contract(): DataContract = contract
    }


    private class ListCells(private val row: List<String?>): RecordCells {
        override val size get() = row.size
        override fun isNull(index: Int) = row[index] == null
        override fun text(index: Int): CharSequence = row[index]!!
    }


    private class ArrayCells: RecordCells {
        var row: Array<String?> = emptyArray()
        override val size get() = row.size
        override fun isNull(index: Int) = row[index] == null
        override fun text(index: Int): CharSequence = row[index]!!
    }


    private data class ReadBack(val names: List<String>, val rows: List<List<String?>>)


    private fun textSchema(names: List<String>, nullable: Boolean = false): DataContract = DataContract(
        DataType.Record(names.map { DataField(FieldId(it, 0), DataType.Scalar(ScalarKind.Text, nullable)) }))


    private fun write(format: ConfiguredDelimitedFormat, names: List<String>, rows: List<List<String?>>): ByteArray {
        val encoder = ConfiguredDelimitedWriterCapability.encoder(format, names)
        val output = DataRecordBuffer()
        encoder.encodeHeader(output)
        for (row in rows) {
            encoder.encodeRecord(ListCells(row), output)
        }
        return output.bytes.copyOf(output.bytesLength)
    }


    private fun read(format: ConfiguredDelimitedFormat, bytes: ByteArray): ReadBack {
        val config = ConfiguredDelimitedReaderCapability.canonicalize(format.baseConfig)
        val request = ReaderOpenRequest(
            "memory://round-trip", null, config, ByteInput(bytes), ReadOperationalPolicy())
        return runBlocking { ConfiguredDelimitedReaderCapability.open(request) }.use { cursor ->
            val fields = (cursor.shape.itemType.structural as DataType.Record).fields
            val read = mutableListOf<List<String?>>()
            while (cursor.hasNext()) {
                val value = cursor.next()
                read += fields.map { field ->
                    val node = value.access.field(value.root, field.id)
                    if (value.access.state(node) == DataState.Null) null else value.access.readText(node)
                }
            }
            ReadBack(fields.map { it.id.name }, read)
        }
    }


    private class ByteInput(private val bytes: ByteArray): ReaderByteInput {
        private var position = 0
        override val expandedBytesRead: Long get() = position.toLong()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (position >= bytes.size) return -1
            val count = minOf(length, bytes.size - position)
            System.arraycopy(bytes, position, buffer, offset, count)
            position += count
            return count
        }
    }
}
