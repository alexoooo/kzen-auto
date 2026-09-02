package tech.kzen.auto.server.objects.job.worker.data

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.file.FileSelectionBrowserConventions
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.ConfiguredDataOpener
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.SchemaCache
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
import tech.kzen.auto.server.objects.datasource.FileDataSource
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.auto.server.objects.job.worker.testJobValue
import tech.kzen.auto.server.objects.job.worker.testProjection
import tech.kzen.auto.server.objects.job.worker.testRecord
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvReportDefiner
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.util.digest.Digest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class FileSourceWorkerTest {
    private val workerLocation = ObjectLocation(
        DocumentPath.parse("test/file-source-worker.yaml"),
        ObjectPath.parse("main.workers/file"))


    @Test
    fun directlyOwnedFileSourceMatchesNominalRead() = runBlocking {
        val directory = Files.createTempDirectory("file-source-worker")
        try {
            val first = directory.resolve("2026-a.csv").also { it.writeText("left,shared\n1,A\n") }
            val second = directory.resolve("2026-b.csv").also { it.writeText("shared,right\nB,2\n") }
            val files = listOf(first, second).map {
                mapOf(FileSelectionEntry.locationKey to it.toString())
            }
            val repository = HostReportDefinitionRepository(listOf(CsvReportDefiner()))
            val listing = FileListingAction(repository)
            val opener = DataOpenerLookup(ConfiguredDataOpener(
                SchemaCache(WorkUtils(directory.resolve("cache")))))

            val directMessages = mutableListOf<DataValue>()
            val format = ConfiguredDelimitedTestFormats.csv()
            FileSourceWorker(
                capturing(directMessages), "", "", files, format, "(?<year>\\d{4})", "fail",
                ReadWorker.emitItems, "", ReadWorker.attributesColumns, workerLocation, opener, listing)
                .run(DirectControl)

            val source = FileDataSource(
                "", "", files, format, "(?<year>\\d{4})", "fail", listing)
            val nominalMessages = mutableListOf<DataValue>()
            val nominal = ReadWorker(
                capturing(nominalMessages), ObjectReference.parse("files"), ReadWorker.emitItems, "",
                ReadWorker.attributesColumns, workerLocation, opener)
            nominal.loadSourceResolution(WorkerDefinitionResolution.Resolved(
                ObjectLocation(workerLocation.documentPath, ObjectPath.parse("main.sources/files")),
                Digest.ofUtf8("files"), source))
            nominal.run(DirectControl)

            assertEquals(nominalMessages.map(::messageValue), directMessages.map(::messageValue))
            assertEquals(
                listOf("year", "left", "shared", "right"),
                testProjection(directMessages.first()).header.values.map { it.text })
        }
        finally {
            WorkUtils.recursivelyDeleteDir(directory)
        }
    }


    @Test
    fun compatibilityKeyCoversOnlyEffectiveFileSourceConfiguration() {
        val files = listOf(mapOf(FileSelectionEntry.locationKey to "a.csv"))
        val schema = schema("a")
        val format = ConfiguredDelimitedTestFormats.csv(schema)
        val base = FileSourceWorker.compatibilityKey(
            "dir", "filter", files, format, "group", "fail")
        val variants = listOf(
            FileSourceWorker.compatibilityKey(
                "other", "filter", files, format, "group", "fail"),
            FileSourceWorker.compatibilityKey(
                "dir", "other", files, format, "group", "fail"),
            FileSourceWorker.compatibilityKey(
                "dir", "filter", listOf(mapOf(FileSelectionEntry.locationKey to "b.csv")),
                format, "group", "fail"),
            FileSourceWorker.compatibilityKey(
                "dir", "filter", files, ConfiguredDelimitedTestFormats.csv(schema, delimiter = "\t"),
                "group", "fail"),
            FileSourceWorker.compatibilityKey(
                "dir", "filter", files, ConfiguredDelimitedTestFormats.csv(schema, "ISO-8859-1"),
                "group", "fail"),
            FileSourceWorker.compatibilityKey(
                "dir", "filter", files, format, "other", "fail"),
            FileSourceWorker.compatibilityKey(
                "dir", "filter", files, format, "group", "skip"),
            FileSourceWorker.compatibilityKey(
                "dir", "filter", files, ConfiguredDelimitedTestFormats.csv(schema("b")),
                "group", "fail"))

        assertTrue(variants.all { it != base })
        assertEquals(base, FileSourceWorker.compatibilityKey(
            "dir", "filter", files, format, "group", "fail"))
    }


    @Test
    fun notationArchetypesAreWorkersWithoutDataSourceCapabilities() {
        val notation = AutoTestUtils.readNotation()
        val attempt = AutoTestUtils.graphDefinitionAttempt(notation)
        for (name in listOf("FileSourceWorker", "LogicSourceWorker")) {
            val location = ObjectLocation.parse("auto-jvm/job/job-worker.yaml#$name")
            assertTrue(attempt.graphStructure.graphMetadata.get(location) != null)
            assertFalse(DataSourceConventions.isDataSource(notation, location))
            assertFalse(DataSourceConventions.isShapeProvider(notation, location))
        }
    }


    @Test
    fun fileWorkerKeepsChooserStateSeparateFromRuntimeDirectoryQuery() {
        val notation = AutoTestUtils.readNotation()
        val attempt = AutoTestUtils.graphDefinitionAttempt(notation)
        val location = ObjectLocation.parse("auto-jvm/job/job-worker.yaml#FileSourceWorker")
        val metadata = attempt.graphStructure.graphMetadata.get(location)!!
        val filesMetadata = metadata.attributes.map[AttributeName("files")]!!

        assertEquals(
            FileSelectionBrowserConventions.defaultDirectory,
            notation.firstAttribute(
                location,
                FileSelectionBrowserConventions.directoryAttributePath(
                    filesMetadata.attributeMetadataNotation)!!)!!.asString())
        assertEquals(
            FileSelectionBrowserConventions.defaultFilter,
            notation.firstAttribute(
                location,
                FileSelectionBrowserConventions.filterAttributePath(
                    filesMetadata.attributeMetadataNotation)!!)!!.asString())
        assertEquals(
            "",
            notation.firstAttribute(location, AttributePath.parse("directory"))!!.asString())
        assertEquals(
            "",
            notation.firstAttribute(location, AttributePath.parse("filter"))!!.asString())
        assertFalse(AttributeName("browser") in metadata.attributes.map)
    }


    private fun schema(name: String): DataSchemaDocument = DataSchemaDocument(
        DataSchemaFieldListSpec(linkedMapOf(name to DataSchemaFieldSpec(TypeMetadata.string))))


    private fun messageValue(message: DataValue): Pair<HeaderListing, List<String>> {
        return testProjection(message).header to testRecord(message).toList()
    }


    private fun capturing(messages: MutableList<DataValue>): ChannelOutput<Any?> {
        return object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                messages.add(testJobValue(element))
            }
            override suspend fun flush() {}
            override fun batchSize(): Int = 1024
            override fun close() {}
        }
    }


    private object DirectControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("File reader needs no scratch directory")
        override fun publishProgress(
            location: ObjectLocation,
            value: Map<String, Any?>,
            force: Boolean
        ) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            error("File reader hosts no child")
    }
}
