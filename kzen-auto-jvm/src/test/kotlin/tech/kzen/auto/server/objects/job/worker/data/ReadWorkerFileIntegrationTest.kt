package tech.kzen.auto.server.objects.job.worker.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
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
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
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
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
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
            val format = CountingAutomaticFormat()
            val source = directorySource(directory, format)
            val firstMessages = mutableListOf<DataValue>()
            val parked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first = readWorker(source, capturing(firstMessages, batchSize = 1))
            val job = launch { first.run(ParkingControl(parked, release)) }
            parked.await()
            val captured = first.captureMigrationState()
            assertEquals(1, format.resolveCount)
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
                assertEquals(
                    1,
                    format.resolveCount,
                    "migration must reuse the captured concrete read spec instead of re-detecting the source")
            }
            finally {
                (captured as AutoCloseable).close()
            }
        }
    }


    @Test
    fun lockedColumnsRejectMissingAndExtraNamesButMapReorderedExactNames() = runBlocking {
        withTempDir { directory ->
            val schema = lockedSchema("left", "right")
            val cases = linkedMapOf(
                "added" to "left,right,extra\n1,2,3\n",
                "removed" to "left\n1\n")

            for ((name, content) in cases) {
                val input = directory.resolve("$name.csv").also { it.writeText(content) }
                val failure = assertFailsWith<tech.kzen.auto.server.data.read.delimited.DelimitedReadException>(name) {
                    readWorker(
                        source(listOf(input), format = ConfiguredDelimitedTestFormats.csv(schema)),
                        capturing(mutableListOf()))
                        .run(CountingControl())
                }
                assertTrue(failure.message.orEmpty().contains(input.fileName.toString()), failure.message)
                assertTrue(failure.message.orEmpty().contains("labels"), failure.message)
            }

            val reordered = directory.resolve("reordered.csv")
                .also { it.writeText("right,left\n2,1\n") }
            val values = mutableListOf<DataValue>()
            readWorker(
                source(listOf(reordered), format = ConfiguredDelimitedTestFormats.csv(schema)),
                capturing(values)).run(CountingControl())
            assertEquals(listOf("left", "right"), headers(values).single().values.map { it.text })
            assertEquals(listOf(listOf("1", "2")), records(values))
        }
    }


    @Test
    fun madeExplicitFormatKeepsItsFrozenDialectAfterSourceContentDrifts() = runBlocking {
        withTempDir { directory ->
            val input = directory.resolve("input.csv")
                .also { it.writeText("name,value\nalpha,1\n") }
            val explicit = ConfiguredDelimitedTestFormats.csv()
            val source = source(listOf(input), format = explicit)
            val before = mutableListOf<DataValue>()
            readWorker(source, capturing(before)).run(CountingControl())
            assertEquals(listOf(listOf("alpha", "1")), records(before))

            input.writeText("name;value\n\"beta;inside\";2\n")
            assertFailsWith<tech.kzen.auto.server.data.read.delimited.DelimitedReadException> {
                readWorker(source, capturing(mutableListOf())).run(CountingControl())
            }

            input.writeText("city,amount,note\nToronto,2,new\n")
            val compatibleDrift = mutableListOf<DataValue>()
            readWorker(source, capturing(compatibleDrift)).run(CountingControl())
            assertEquals(listOf("city", "amount", "note"),
                headers(compatibleDrift).single().values.map { it.text })
            assertEquals(listOf(listOf("Toronto", "2", "new")), records(compatibleDrift))
        }
    }


    @Test
    fun capturedPreLockManifestContinuesWhileFreshResolutionAdoptsTheLockedContract() = runBlocking {
        withTempDir { directory ->
            val input = directory.resolve("input.csv")
                .also { it.writeText("left,right\none,1\ntwo,2\n") }
            val switching = SwitchingFormat(ConfiguredDelimitedTestFormats.csv())
            val source = source(listOf(input), format = switching)
            val beforeLock = mutableListOf<DataValue>()
            val parked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first = readWorker(source, capturing(beforeLock, batchSize = 1))
            val job = launch { first.run(ParkingControl(parked, release)) }
            parked.await()
            val captured = first.captureMigrationState()
            job.cancelAndJoin()

            switching.delegate = ConfiguredDelimitedTestFormats.csv(lockedSchema("left", "right"))
            Files.delete(input)
            input.writeText("renamed,left\n3,three\n")

            try {
                val resumed = mutableListOf<DataValue>()
                val migrated = readWorker(source, capturing(resumed, batchSize = 1))
                migrated.loadMigrationState(captured)
                migrated.run(CountingControl())
                assertEquals(
                    listOf(listOf("one", "1"), listOf("two", "2")),
                    records(beforeLock + resumed),
                    "migration must retain the captured pre-lock manifest and open cursor")

                val freshFailure = assertFailsWith<tech.kzen.auto.server.data.read.delimited.DelimitedReadException> {
                    readWorker(source, capturing(mutableListOf())).run(CountingControl())
                }
                assertTrue(freshFailure.message.orEmpty().contains("labels"), freshFailure.message)
            }
            finally {
                (captured as AutoCloseable).close()
            }
        }
    }


    private fun source(
        files: List<Path>,
        groupPattern: String = "",
        format: ConfiguredRecordFormat = ConfiguredDelimitedTestFormats.csv()
    ): FileDataSource {
        return FileDataSource(
            "", "",
            files.map { mapOf(FileSelectionEntry.locationKey to it.toString()) },
            format, groupPattern, FileDataSource.missingFail, listing)
    }


    private fun directorySource(
        directory: Path,
        format: ConfiguredRecordFormat = ConfiguredDelimitedTestFormats.csv()
    ): FileDataSource {
        return FileDataSource(
            directory.toString(), "", emptyList(), format, "",
            FileDataSource.missingFail, listing)
    }


    private class CountingAutomaticFormat: ConfiguredRecordFormat {
        private val delegate = ConfiguredDelimitedTestFormats.csv()

        var resolveCount: Int = 0
            private set

        override val title: String = "Automatic test format"
        override val extensions: List<String> = emptyList()
        override val catalogVisible: Boolean = false
        override val selectionKind: FormatSelectionKind = FormatSelectionKind.Automatic
        override val automaticDetectionCandidate: Boolean = false


        override suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult {
            resolveCount += 1
            val resolved = delegate.resolve(request)
            return resolved.copy(detail = resolved.detail.copy(
                concreteFormatReference = "test/configured-format.yaml#ConfiguredCsv",
                displayLabel = delegate.title,
                selection = FormatSelectionKind.Automatic,
                basis = FormatResolutionBasis.Content,
                reason = "CSV was detected from content"))
        }


        @Suppress("OVERRIDE_DEPRECATION")
        override fun resolvedRead(ref: tech.kzen.auto.common.data.model.DataRef) = delegate.resolvedRead(ref)


        override fun declaredShape(): DataShape? = null


        override fun digest(sink: Digest.Sink) {
            sink.addUtf8(title)
        }
    }


    private class SwitchingFormat(
        var delegate: ConfiguredRecordFormat
    ): ConfiguredRecordFormat {
        override val title: String get() = delegate.title
        override val extensions: List<String> get() = delegate.extensions
        override val catalogVisible: Boolean get() = delegate.catalogVisible
        override val automaticDetectionCandidate: Boolean get() = delegate.automaticDetectionCandidate
        override val authoringCapabilityIdentity: String? get() = delegate.authoringCapabilityIdentity
        override val overrideEditorReference: String? get() = delegate.overrideEditorReference
        override val columnsLocked: Boolean get() = delegate.columnsLocked

        override suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult =
            delegate.resolve(request)

        @Suppress("OVERRIDE_DEPRECATION")
        override fun resolvedRead(ref: tech.kzen.auto.common.data.model.DataRef) = delegate.resolvedRead(ref)

        override fun declaredShape(): DataShape? = delegate.declaredShape()

        override fun digest(sink: Digest.Sink) = delegate.digest(sink)
    }


    private fun lockedSchema(vararg fields: String): DataSchemaDocument = DataSchemaDocument(
        DataSchemaFieldListSpec(linkedMapOf(*fields.map { name ->
            name to DataSchemaFieldSpec(TypeMetadata.string)
        }.toTypedArray())))


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
