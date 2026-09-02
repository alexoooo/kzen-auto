package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.DelimitedDialectSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.common.data.read.TypedDecodePolicy
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.objects.document.logic.BindingSignatureDefiner
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReader
import tech.kzen.auto.server.data.read.delimited.DelimitedReadContext
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import java.io.InputStream
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
 * [ChannelInput] of flat-backed values writing to a temp file, then reads the file back — DECOMPRESSING per the
 * configured compression and re-parsing with the configured reader — and asserts the records survive
 * exactly (the P4f "gz|zip|none → read back == input" gate).
 *
 * This exercises the reused export leaf engines end-to-end: the CSV / TSV [RecordFormat][tech.kzen.auto.server.objects.report.exec.output.export.format.RecordFormat]
 * (RFC-4180 quoting is the inverse of the configured delimited reader, so quoted fields round-trip), the UTF-8
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

    private fun record(city: String, amount: String): DataValue =
        JobDataValues.flat(header, FlatFileRecord.of(listOf(city, amount)))

    // Rows with a comma and an embedded quote, so RFC-4180 quoting has to survive the compression round-trip.
    private val records = listOf(
        record("Lviv", "10"),
        record("Kyiv, UA", "20"),
        record("O'Brien \"Q\"", "30"))

    // The configured reader consumes the header and returns only data records.
    private val expectedCsvRows = listOf(
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
                val bytes = decompress(compression, file)
                assertHeader(bytes, ",")
                assertEquals(expectedCsvRows, parseRecords(bytes, ","))
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
        input: List<DataValue> = records
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
            assertHeader(bytes, readDelimiter)
            return parseRecords(bytes, readDelimiter)
        }
        finally {
            Files.deleteIfExists(file)
        }
    }


    private fun assertHeader(bytes: ByteArray, delimiter: String) {
        assertTrue(bytes.decodeToString().startsWith("city${delimiter}amount\n"))
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


    // Re-parse through the maintained configured reader (the inverse of the CSV export formatter).
    private fun parseRecords(bytes: ByteArray, delimiter: String): List<List<String>> {
        val config = DelimitedReadConfig(
            RecordFramingSpec("lf"),
            DelimitedDialectSpec(delimiter, "\"", "double-quote", "empty", "none"),
            HeaderReadSpec("present", "exact-name"),
            CharacterDecodingSpec("UTF-8", "forbid", "report", "report"),
            null,
            TypedDecodePolicy(null, "fail-part", emptyList()))
        return ConfiguredDelimitedReader.open(
            ByteStringContent(bytes.decodeToString()),
            config,
            ReadOperationalPolicy(),
            DelimitedReadContext("memory://export-round-trip"))
            .use { reader ->
                generateSequence(reader::read).map { it.backing.toList() }.toList()
            }
    }


    private class ByteStringContent(private val text: String): SequentialCharacterContent {
        override val resolvedCharsetName = "UTF-8"
        override val inspectionRecordLimit = Long.MAX_VALUE
        private var position = 0

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (position >= text.length) return -1
            val count = minOf(length, text.length - position)
            text.toCharArray(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun close() = Unit
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun chunkedInput(input: List<DataValue>): ChannelInput<Any?> =
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
        override fun results(): BindingSchema = BindingSchema.of(
            BindingDefinition(BindingName("artifact"), BindingSignatureDefiner.contract(resultType)))
        override fun yieldResult(component: String, value: DataValue) {
            assertEquals("artifact", component)
            yielded = JobDataValues.boundary(value)
        }
        override suspend fun host(instructions: ObjectLocation, input: Any?) = error("unused")
    }
}
