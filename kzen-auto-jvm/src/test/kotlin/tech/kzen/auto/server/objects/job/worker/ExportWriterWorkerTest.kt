package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.AfterTest


/**
 * Round-trip test for [ExportWriterWorker]: drives the sink's real [ExportWriterWorker.run] lifecycle over a fake
 * [ChannelInput] of flat-part [JobMessage]s writing to a temp file, then reads the file back — DECOMPRESSING per the
 * configured compression and re-parsing with the Job's own [CsvRecordReader] — and asserts the records survive
 * exactly (the P4f "gz|zip|none → read back == input" gate).
 *
 * This exercises the reused export leaf engines end-to-end: the CSV / TSV [RecordFormat][tech.kzen.auto.server.objects.report.exec.output.export.format.RecordFormat]
 * (RFC-4180 quoting is the inverse of `CsvRecordReader`, so fields with commas / quotes round-trip), the UTF-8
 * encode, and the shared [ExportCompression.wrap][tech.kzen.auto.server.objects.report.exec.output.export.model.ExportCompression]
 * none/zip/gz seam. The header row is written once (from the first record). Byte-identical parity against a real
 * `ReportRun` export is the separate P4j gate.
 */
class ExportWriterWorkerTest {
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val header = HeaderListing.of(listOf("city", "amount"))

    private fun record(city: String, amount: String): JobMessage =
        JobMessage.ofFlat(header, FlatFileRecord.of(listOf(city, amount)))

    // Rows with a comma and an embedded quote, so RFC-4180 quoting has to survive the compression round-trip.
    private val records = listOf(
        record("Lviv", "10"),
        record("Kyiv, UA", "20"),
        record("O'Brien \"Q\"", "30"))

    // What a read-back should yield: the header row, then the data rows.
    private val expectedCsvRows = listOf(
        listOf("city", "amount"),
        listOf("Lviv", "10"),
        listOf("Kyiv, UA", "20"),
        listOf("O'Brien \"Q\"", "30"))

    private val selfLocation = ObjectLocation(
        DocumentPath.parse("test/export-unit-test.yaml"),
        ObjectPath.parse("main.workers/export"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun csvNoneRoundTripsRecordsWithQuoting() = runBlocking {
        assertEquals(expectedCsvRows, roundTrip("csv", OutputExportSpec.compressionNoneName, ","))
    }


    @Test
    fun csvZipRoundTripsRecordsWithQuoting() = runBlocking {
        assertEquals(expectedCsvRows, roundTrip("csv", OutputExportSpec.compressionZipName, ","))
    }


    @Test
    fun csvGzRoundTripsRecordsWithQuoting() = runBlocking {
        assertEquals(expectedCsvRows, roundTrip("csv", OutputExportSpec.compressionGzName, ","))
    }


    @Test
    fun tsvRoundTripsTabDelimited() = runBlocking {
        // TSV is tab-delimited and does not quote, so use plain fields (no tab / quote), read back with a tab delimiter.
        val tsvRecords = listOf(record("Lviv", "10"), record("Kyiv", "20"))
        val expected = listOf(
            listOf("city", "amount"),
            listOf("Lviv", "10"),
            listOf("Kyiv", "20"))

        assertEquals(expected, roundTrip("tsv", OutputExportSpec.compressionNoneName, "\t", tsvRecords))
    }


    @Test
    fun yieldedRefFingerprintsTheFinalizedContainer() = runBlocking {
        for (compression in listOf(
            OutputExportSpec.compressionNoneName,
            OutputExportSpec.compressionGzName,
            OutputExportSpec.compressionZipName)) {
            val file = Files.createTempFile("exportworker-yield", ".$compression")
            try {
                val control = YieldControl()
                val worker = ExportWriterWorker(
                    chunkedInput(records),
                    OutputExportSpec("csv", compression, file.toString()),
                    "artifact", selfLocation,
                    FileListingAction(HostReportDefinitionRepository(emptyList())), compiler())
                worker.run(control)

                val ref = control.yielded as DataRef
                assertEquals(file.toAbsolutePath().normalize(), Path.of(ref.id))
                assertEquals(Files.size(file).toString(), ref.attributes[DataRef.sizeKey])
                assertNotNull(ref.attributes[DataRef.modifiedKey])
                assertEquals(expectedCsvRows, parseRecords(decompress(compression, file), ","))
            }
            finally {
                Files.deleteIfExists(file)
            }
        }
    }


    @Test
    fun activeWriterRejectsAResultThatCannotAcceptDataRefBeforeOpening() = runBlocking {
        val root = Files.createTempDirectory("export-writer-result-type")
        try {
            val path = root.resolve("wrong.csv")
            val failure = assertFailsWith<IllegalArgumentException> {
                ExportWriterWorker(
                    chunkedInput(records),
                    OutputExportSpec("csv", OutputExportSpec.compressionNoneName, path.toString()),
                    "artifact", selfLocation,
                    FileListingAction(HostReportDefinitionRepository(emptyList())), compiler())
                    .run(YieldControl(TypeMetadata.string))
            }
            assertTrue(failure.message.orEmpty().contains("writer yields DataRef"))
            assertFalse(Files.exists(path))
        }
        finally {
            root.toFile().deleteRecursively()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun roundTrip(
        format: String,
        compression: String,
        readDelimiter: String,
        input: List<JobMessage> = records
    ): List<List<String>> {
        val file = Files.createTempFile("exportworker", ".out")
        try {
            // The path pattern is the literal temp file (no ${…} placeholders), so it is written to verbatim.
            val export = OutputExportSpec(format, compression, file.toString())
            val worker = ExportWriterWorker(
                chunkedInput(input), export, "", selfLocation,
                FileListingAction(HostReportDefinitionRepository(emptyList())), compiler())
            worker.run(NoOpJobControl)

            val bytes = decompress(compression, file)
            return parseRecords(bytes, readDelimiter)
        }
        finally {
            Files.deleteIfExists(file)
        }
    }


    private fun decompress(compression: String, file: Path): ByteArray {
        val rawInput: InputStream = Files.newInputStream(file)
        return when (compression) {
            OutputExportSpec.compressionNoneName ->
                rawInput.use { it.readBytes() }

            OutputExportSpec.compressionGzName ->
                // MiGz output is standard concatenated-member gzip, which GZIPInputStream reads.
                GZIPInputStream(rawInput).use { it.readBytes() }

            OutputExportSpec.compressionZipName ->
                ZipInputStream(rawInput).use { zip ->
                    zip.nextEntry ?: error("empty zip archive")
                    zip.readBytes()
                }

            else ->
                error("unsupported: $compression")
        }
    }


    // Re-parse the exported bytes with the Job's own CSV reader (the inverse of the CSV export formatter).
    private fun parseRecords(bytes: ByteArray, delimiter: String): List<List<String>> {
        val reader = CsvRecordReader(
            InputStreamReader(ByteArrayInputStream(bytes), StandardCharsets.UTF_8), delimiter)
        return generateSequence { reader.readRecord() }
            .map { it.toList() }
            .toList()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun chunkedInput(input: List<JobMessage>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            private var delivered = false

            override suspend fun receiveBatch(): List<Any?>? {
                if (delivered || input.isEmpty()) {
                    return null
                }
                delivered = true
                return input
            }

            override suspend fun receive(): Any? = error("unused")
            override fun iterator(): ChannelInputIterator<Any?> = error("unused")
        }


    //-----------------------------------------------------------------------------------------------------------------
    // An ExportWriterWorker only consumes + writes files (through runBlockingIo) + publishes; nothing else here.
    private object NoOpJobControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("An ExportWriterWorker writes the user path, needs no scratch dir")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("An ExportWriterWorker hosts no child")
    }


    private fun compiler() = testContext().cachedKotlinCompiler


    private fun testContext(): KzenAutoContext {
        if (!::context.isInitialized) context = KzenAutoContext.forTest()
        return context
    }


    private class YieldControl(
        private val resultType: TypeMetadata = TypeMetadata.anyNullable
    ): JobControl {
        var yielded: Any? = null

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override fun results(): TupleDefinition = TupleDefinition(listOf(
            TupleComponentDefinition(TupleComponentName("artifact"), LogicType(resultType))))
        override fun yieldResult(component: String, value: Any?) {
            assertEquals("artifact", component)
            yielded = value
        }
        override suspend fun host(instructions: ObjectLocation, input: Any?) = error("unused")
    }
}
