package tech.kzen.auto.server.objects.job.worker.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.ConfiguredDataOpener
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.FlatDataLocation
import tech.kzen.auto.server.data.SchemaCache
import tech.kzen.auto.server.objects.datasource.FileDataSource
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedFormat
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.auto.server.objects.job.worker.testJobValue
import tech.kzen.auto.server.objects.job.worker.testProjection
import tech.kzen.auto.server.objects.job.worker.testRecord
import tech.kzen.auto.server.objects.job.worker.compatibility.LegacyCsvSourceWorker
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.parse.text.TextReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.parse.tsv.TsvReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.model.data.DatasetInfo
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataInfo
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.util.digest.Digest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class ReadWorkerFileIntegrationTest {
    private val workerLocation = ObjectLocation(
        DocumentPath.parse("test/read-file-integration.yaml"),
        ObjectPath.parse("main.workers/read"))
    private val sourceLocation = ObjectLocation(
        workerLocation.documentPath,
        ObjectPath.parse("main.sources/files"))
    private val repository = HostReportDefinitionRepository(listOf(
        CsvReportDefiner(), TsvReportDefiner(), TextReportDefiner()))
    private val listing = FileListingAction(repository)
    private val opener = ConfiguredDataOpener(
        SchemaCache(WorkUtils(Files.createTempDirectory("read-worker-file-cache"))))


    @Test
    fun nominalCompatibilityReaderUsesConfiguredHeaderlessDelimiter() = runBlocking {
        withTempDir { directory ->
            val input = directory.resolve("input.txt").also {
                it.writeText("alpha;1\nbeta;2\n")
            }
            val messages = mutableListOf<DataValue>()
            LegacyCsvSourceWorker(
                capturing(messages),
                input.toString(),
                ";",
                false,
                workerLocation,
                DataOpenerLookup(opener),
                listing).run(CountingControl())

            assertEquals(listOf("c0", "c1"),
                testProjection(messages.first()).header.values.map { it.text })
            assertEquals(listOf(listOf("alpha", "1"), listOf("beta", "2")), records(messages))
        }
    }


    @Test
    fun configuredCsvReaderHandlesRfc4180EdgeCases() = runBlocking {
        withTempDir { directory ->
            val csv = directory.resolve("edge.csv").also {
                it.writeText(
                    "name,note\r\nalpha,\"comma, inside\"\r\n" +
                        "beta,\"line1\nline2\"\r\ngamma,\"quote \"\"inside\"\"\"\r\n")
            }

            val modern = mutableListOf<DataValue>()
            readWorker(source(
                listOf(csv),
                format = ConfiguredDelimitedTestFormats.csv(recordSeparator = "crlf")),
                capturing(modern)).run(CountingControl())

            assertEquals(
                listOf(
                    listOf("alpha", "comma, inside"),
                    listOf("beta", "line1\nline2"),
                    listOf("gamma", "quote \"inside\"")),
                records(modern))
            assertEquals(
                listOf("name", "note"),
                headers(modern).distinct().single().values.map { it.text })
        }
    }


    @Test
    fun configuredReaderPreservesMultiFileOrderAndSkipsEachHeader() = runBlocking {
        withTempDir { directory ->
            val first = directory.resolve("a.csv")
                .also { it.writeText("city,amount\nLviv,10\nKyiv,20\n") }
            val second = directory.resolve("b.csv")
                .also { it.writeText("city,amount\nOdesa,30\nDnipro,40\n") }

            val modern = mutableListOf<DataValue>()
            val control = CountingControl()
            readWorker(source(listOf(first, second)), capturing(modern)).run(control)

            assertEquals(
                listOf(
                    listOf("Lviv", "10"),
                    listOf("Kyiv", "20"),
                    listOf("Odesa", "30"),
                    listOf("Dnipro", "40")),
                records(modern))
            assertEquals(
                List(4) { listOf("city", "amount") },
                headers(modern).map { header -> header.values.map { it.text } })
            assertTrue(control.blockingCount >= modern.size)
        }
    }


    @Test
    fun defaultSupersetProjectsDifferentRealHeadersToReportOrderedUnion() = runBlocking {
        withTempDir { directory ->
            val first = directory.resolve("a.csv").also { it.writeText("left,shared\n1,A\n") }
            val second = directory.resolve("b.csv").also { it.writeText("shared,right\nB,2\n") }
            val messages = mutableListOf<DataValue>()

            readWorker(source(listOf(first, second)), capturing(messages)).run(CountingControl())

            val expected = HeaderListing.ofUnique(listOf("left", "shared", "right"))
            assertEquals(listOf(expected, expected), headers(messages))
            assertEquals(
                listOf(
                    listOf("1", "A", LegacyDataShapeBridge.missingCellValue),
                    listOf(LegacyDataShapeBridge.missingCellValue, "B", "2")),
                records(messages))

            val reportSuperset = DatasetInfo(listOf(
                reportInfo(first, HeaderListing.ofUnique(listOf("left", "shared"))),
                reportInfo(second, HeaderListing.ofUnique(listOf("shared", "right")))
            )).headerSuperset()
            assertEquals(reportSuperset, expected, "Read's physical projection matches Report's header union")
        }
    }


    @Test
    fun explicitStrictRejectsDifferentRealHeadersNamingBothParts() = runBlocking {
        withTempDir { directory ->
            val first = directory.resolve("a.csv").also { it.writeText("left\n1\n") }
            val second = directory.resolve("b.csv").also { it.writeText("right\n2\n") }

            val failure = assertFailsWith<IllegalStateException> {
                readWorker(
                    source(listOf(first, second)), capturing(mutableListOf()),
                    schemaMode = DataReadCore.schemaStrict)
                    .run(CountingControl())
            }

            assertTrue(failure.message.orEmpty().contains(first.fileName.toString()))
            assertTrue(failure.message.orEmpty().contains(second.fileName.toString()))
        }
    }


    @Test
    fun realGroupPatternAttributesBecomeLeadingColumns() = runBlocking {
        withTempDir { directory ->
            val file = directory.resolve("2026-08-sales.csv")
                .also { it.writeText("amount\n10\n") }
            val messages = mutableListOf<DataValue>()
            readWorker(
                source(listOf(file), "(?<year>\\d{4})-(?<month>\\d{2})"),
                capturing(messages), ReadWorker.attributesColumns)
                .run(CountingControl())

            val value = messages.single()
            assertEquals(listOf("year", "month", "amount"), testProjection(value).header.values.map { it.text })
            assertEquals(listOf("2026", "08", "10"), testRecord(value).toList())
        }
    }


    @Test
    fun realFileUnitsEmitInAuthoredExplicitOrderWithoutOpening() = runBlocking {
        withTempDir { directory ->
            val files = listOf("c.csv", "a.csv", "b.csv").mapIndexed { index, name ->
                directory.resolve(name).also { it.writeText("value\n$index\n") }
            }
            val messages = mutableListOf<DataValue>()
            readWorker(
                source(files), capturing(messages), emit = ReadWorker.emitUnits)
                .run(CountingControl())

            assertEquals(
                files.map {
                    DataLocation.of(it.toAbsolutePath().normalize().toString()).asString()
                },
                messages.map { (JobDataValues.boundary(it) as DataUnit).parts.single().ref.id })
        }
    }


    @Test
    fun realCursorAndCapturedDirectoryManifestTransferAcrossMigration() = runBlocking {
        withTempDir { directory ->
            val original = directory.resolve("a.csv")
                .also { it.writeText("value\none\ntwo\nthree\n") }
            val source = directorySource(directory)
            val firstMessages = mutableListOf<DataValue>()
            val parked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first = readWorker(source, capturing(firstMessages, batchSize = 1))
            val job = launch { first.run(ParkingControl(parked, release)) }
            parked.await()
            val captured = first.captureMigrationState()
            job.cancelAndJoin()

            try {
                // Windows/JDK 26 permits unlinking this open stream. The live detached cursor must nevertheless
                // retain ownership and deliver its already-open content to the resumed worker.
                Files.delete(original)
                directory.resolve("b.csv").writeText("value\nnew-file\n")

                val resumedMessages = mutableListOf<DataValue>()
                val resumed = readWorker(source, capturing(resumedMessages, batchSize = 1))
                resumed.loadMigrationState(captured)
                resumed.run(CountingControl())

                assertEquals(
                    listOf(listOf("one"), listOf("two"), listOf("three")),
                    records(firstMessages + resumedMessages),
                    "the captured manifest and cursor exclude files added after the migration cut")
            }
            finally {
                (captured as AutoCloseable).close()
            }
        }
    }


    private fun source(
        files: List<Path>,
        groupPattern: String = "",
        format: ConfiguredDelimitedFormat = ConfiguredDelimitedTestFormats.csv()
    ): FileDataSource {
        return FileDataSource(
            "", "",
            files.map { mapOf(FileSelectionEntry.locationKey to it.toString()) },
            format, groupPattern, FileDataSource.missingFail, listing)
    }


    private fun directorySource(directory: Path): FileDataSource {
        return FileDataSource(
            directory.toString(), "", emptyList(), ConfiguredDelimitedTestFormats.csv(), "",
            FileDataSource.missingFail, listing)
    }


    private fun readWorker(
        source: FileDataSource,
        output: ChannelOutput<Any?>,
        attributes: String = ReadWorker.attributesIgnore,
        emit: String = ReadWorker.emitItems,
        schemaMode: String = DataReadCore.schemaSuperset
    ): ReadWorker {
        val worker = ReadWorker(
            output, ObjectReference.parse("files"), emit, "", attributes,
            workerLocation, DataOpenerLookup(opener), schemaMode)
        worker.loadSourceResolution(
            WorkerDefinitionResolution.Resolved(
                sourceLocation, Digest.ofUtf8("files"), source))
        return worker
    }


    private fun records(messages: List<DataValue>): List<List<String>> {
        return messages.map { testRecord(it).toList() }
    }


    private fun headers(messages: List<DataValue>): List<HeaderListing> {
        return messages.map { testProjection(it).header }
    }


    private fun reportInfo(path: Path, header: HeaderListing): FlatDataInfo {
        return FlatDataInfo(
            FlatDataLocation(DataLocation.of(path.toString()), DataEncodingSpec.utf8),
            header,
            PluginCoordinate("CSV"),
            DataLocationGroup.empty)
    }


    private fun capturing(
        sink: MutableList<DataValue>,
        batchSize: Int = 1024
    ): ChannelOutput<Any?> {
        return object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                sink.add(testJobValue(element))
            }
            override suspend fun flush() {}
            override fun batchSize(): Int = batchSize
            override fun close() {}
        }
    }


    private open class CountingControl: JobControl {
        var blockingCount = 0


        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R {
            blockingCount += 1
            return block()
        }
        override fun scratchDir(): String = error("Reader needs no scratch directory")
        override fun publishProgress(
            location: ObjectLocation,
            value: Map<String, Any?>,
            force: Boolean
        ) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            error("Reader hosts no child")
    }


    private class ParkingControl(
        private val parked: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>
    ): CountingControl() {
        private var checkpoints = 0


        override suspend fun checkpoint() {
            checkpoints += 1
            if (checkpoints == 2) {
                parked.complete(Unit)
                release.await()
            }
        }
    }


    private inline fun <R> withTempDir(use: (Path) -> R): R {
        val directory = Files.createTempDirectory("read-worker-file")
        try {
            return use(directory)
        }
        finally {
            WorkUtils.recursivelyDeleteDir(directory)
        }
    }
}
