package tech.kzen.auto.server.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.plugin.api.HeaderExtractor
import tech.kzen.auto.plugin.api.managed.TraversableReportOutput
import tech.kzen.auto.plugin.definition.ReportDefiner
import tech.kzen.auto.plugin.definition.ReportDefinition
import tech.kzen.auto.plugin.definition.ReportDefinitionInfo
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.server.objects.job.worker.CsvRecordReader
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.parse.text.TextReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.parse.tsv.TsvReportDefiner
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.auto.server.util.WorkUtils
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


class FileDataOpenerTest {
    private class TestContext: DataContext {
        var valid = true
        override fun argument(name: String): Any? = null
        override suspend fun <R> blocking(block: () -> R): R {
            check(valid) { "invalidated" }
            return block()
        }
    }

    private val schemaCache = SchemaCache(WorkUtils(Files.createTempDirectory("file-opener-cache")))
    private val opener = FileDataOpener(HostReportDefinitionRepository(listOf(
        CsvReportDefiner(), TsvReportDefiner(), TextReportDefiner())), schemaCache)

    private fun part(
        path: Path,
        format: String? = null,
        encoding: String? = null
    ) = DataPart(
        DataRole.main,
        DataRef.ofLocation(tech.kzen.auto.common.util.data.DataLocation.of(path.toString())),
        format?.let(CommonPluginCoordinate::ofString),
        encoding?.let(CommonDataEncodingSpec::ofString))


    private fun fingerprintedPart(path: Path): DataPart = DataPart(
        DataRole.main,
        DataRef(
            null,
            path.toString(),
            mapOf(
                DataRef.sizeKey to Files.size(path).toString(),
                DataRef.modifiedKey to Files.getLastModifiedTime(path).toMillis().toString())),
        null,
        null)

    private fun read(
        path: Path,
        format: String? = null,
        encoding: String? = null,
        context: TestContext = TestContext()
    ): Pair<DataShape?, List<List<String>>> {
        val cursor = runBlocking { opener.open(context, part(path, format, encoding)) }
        cursor.use {
            val rows = mutableListOf<List<String>>()
            while (cursor.hasNext()) {
                rows.add((cursor.next() as FlatFileRecord).toList())
            }
            return cursor.shape to rows
        }
    }

    private fun directCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        CsvRecordReader(StringReader(text), ",").use { reader ->
            while (true) {
                rows.add(reader.readRecord()?.toList() ?: break)
            }
        }
        return rows
    }


    @Test
    fun csvMatchesDirectRfc4180ReaderAfterHeader() {
        val text = "name,note\r\nalpha,\"comma, inside\"\r\nbeta,\"line1\nline2\"\r\n"
        val file = Files.createTempFile("file-opener-edge", ".csv").also { it.writeText(text) }

        val (shape, rows) = read(file)

        assertEquals(directCsv(text).drop(1), rows)
        assertEquals(listOf("name", "note"),
            assertIs<DataShape.Tabular>(shape).header.values.map { it.text })
    }


    @Test
    fun boundedInspectionReturnsHeaderCachesExactFingerprintAndReleasesFile() {
        val file = Files.createTempFile("file-opener-inspect", ".csv")
            .also { it.writeText("city,amount\nLviv,10\n") }
        val part = fingerprintedPart(file)

        val shape = runBlocking { opener.inspectShape(TestContext(), part) }
        assertEquals(
            listOf("city", "amount"),
            assertIs<DataShape.Tabular>(shape).header.values.map { it.text })

        file.deleteExisting()
        val cached = runBlocking { opener.inspectShape(TestContext(), part) }
        assertEquals(shape, cached)
    }


    @Test
    fun unfingerprintedInspectionDoesNotHideAChangedHeader() {
        val file = Files.createTempFile("file-opener-inspect-fresh", ".csv")
            .also { it.writeText("a\n1\n") }
        assertEquals(
            listOf("a"),
            assertIs<DataShape.Tabular>(
                runBlocking { opener.inspectShape(TestContext(), part(file)) }).header.values.map { it.text })

        file.writeText("b,c\n2,3\n")
        assertEquals(
            listOf("b", "c"),
            assertIs<DataShape.Tabular>(
                runBlocking { opener.inspectShape(TestContext(), part(file)) }).header.values.map { it.text })
    }


    @Test
    fun extensionInferenceAndExplicitCoordinateAreIndependent() {
        val tsv = Files.createTempFile("file-opener", ".tsv")
            .also { it.writeText("left\tright\nA\tB\n") }
        assertEquals(listOf(listOf("A", "B")), read(tsv).second)

        val misleading = Files.createTempFile("file-opener-explicit", ".csv")
            .also { it.writeText("left\tright\nA\tB\n") }
        assertEquals(listOf(listOf("A", "B")), read(misleading, "TSV").second)
    }


    @Test
    fun explicitEncodingOverridesPluginDefaultWhileNullUsesDefault() {
        val file = Files.createTempFile("file-opener-latin1", ".csv")
        file.writeBytes("word\ncafé\n".toByteArray(StandardCharsets.ISO_8859_1))

        val explicit = read(file, encoding = "ISO-8859-1").second
        val byDefault = read(file).second

        assertEquals(listOf(listOf("café")), explicit)
        assertNotEquals(explicit, byDefault)
    }


    @Test
    fun gzipUsesInnerExtensionAndReadsTransparently() {
        val file = Files.createTempFile("file-opener", ".csv.gz")
        GZIPOutputStream(Files.newOutputStream(file)).use {
            it.write("name\ncompressed\n".toByteArray(StandardCharsets.UTF_8))
        }

        assertEquals(listOf(listOf("compressed")), read(file).second)
    }


    @Test
    fun unknownCoordinateAndUnknownExtensionFailClearly() {
        val file = Files.createTempFile("file-opener-unknown", ".wat").also { it.writeText("x") }
        val unknownCoordinate = assertFailsWith<IllegalArgumentException> {
            runBlocking { opener.open(TestContext(), part(file, "MissingFormat")) }
        }
        assertTrue(unknownCoordinate.message!!.contains("Unknown data format"))

        val unknownExtension = assertFailsWith<IllegalArgumentException> {
            runBlocking { opener.open(TestContext(), part(file)) }
        }
        assertTrue(unknownExtension.message!!.contains("Unable to infer data format"))
    }


    @Test
    fun throwingHeaderExtractorClosesItsFileStream() {
        val csvDefinition = CsvReportDefiner.instance.define()
        val throwingDefiner = object: ReportDefiner<FlatFileRecord> {
            override fun info() = ReportDefinitionInfo(
                PluginCoordinate("ThrowingHeader"), listOf("throwing"), DataEncodingSpec.utf8)

            override fun define() = ReportDefinition(
                csvDefinition.reportDataDefinition,
                {
                    object: HeaderExtractor<FlatFileRecord> {
                        override fun extract(processed: TraversableReportOutput<FlatFileRecord>): List<String> {
                            processed.poll { }
                            throw IllegalStateException("header extraction failed")
                        }
                    }
                })
        }
        val throwingOpener = FileDataOpener(
            HostReportDefinitionRepository(listOf(throwingDefiner)), schemaCache)
        val file = Files.createTempFile("file-opener-throwing-header", ".throwing")
            .also { it.writeText("name\nvalue\n") }

        val failure = assertFailsWith<IllegalStateException> {
            runBlocking { throwingOpener.open(TestContext(), part(file)) }
        }
        assertEquals("header extraction failed", failure.message)
        file.deleteExisting()
    }


    @Test
    fun cursorOutlivesOpeningContextAndHasStableEofAndClose() {
        val file = Files.createTempFile("file-opener-lifecycle", ".csv")
            .also { it.writeText("name\nfirst\nsecond\n") }
        val context = TestContext()
        val cursor = runBlocking { opener.open(context, part(file)) }
        context.valid = false

        assertTrue(cursor.hasNext())
        assertEquals(listOf("first"), (cursor.next() as FlatFileRecord).toList())
        assertEquals(listOf("second"), (cursor.next() as FlatFileRecord).toList())
        assertFalse(cursor.hasNext())
        assertFalse(cursor.hasNext())
        cursor.close()
        cursor.close()
        file.deleteExisting()
    }


    @Test
    fun cancellationAfterBlockingAcquisitionClosesCursorBeforeHandoff() {
        val file = Files.createTempFile("file-opener-cancel-handoff", ".csv")
            .also { it.writeText("name\nvalue\n") }
        val cancellingContext = object: DataContext {
            override fun argument(name: String): Any? = null
            override suspend fun <R> blocking(block: () -> R): R {
                block()
                throw CancellationException("cancelled after acquisition")
            }
        }

        assertFailsWith<CancellationException> {
            runBlocking { opener.open(cancellingContext, part(file)) }
        }
        file.deleteExisting()
    }
}
