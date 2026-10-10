package tech.kzen.auto.server.objects.job.worker.format

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobFieldConventions
import tech.kzen.auto.common.objects.document.job.JobOutputConventions
import tech.kzen.auto.server.data.design.DesignReadBudget
import tech.kzen.auto.server.data.design.DesignReader
import tech.kzen.auto.server.objects.job.JobValidator
import tech.kzen.auto.server.objects.job.worker.content.Bytes
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness
import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.walk
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * `Format` (WR3, docs/plans/2026-10-07_format-and-write.md §4.4; FG2,
 * docs/plans/2026-10-09_format-groups-and-write-naming.md §4.3): each record becomes one pooled chunk of [Bytes] in
 * its group's output, the chunk that starts an output carrying the header made from the input lane's columns and a
 * last chunk marking its end; what the format cannot write is refused before Run, and a record it cannot write fails
 * the run by its position.
 */
class FormatWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val superset = "test/job/format/format-superset.yaml"
        private const val column = "test/job/format/format-column.yaml"
        private const val empty = "test/job/format/format-empty.yaml"
        private const val schema = "test/job/format/format-schema.yaml"
        private const val record = "test/job/format/format-record.yaml"
        private const val recycle = "test/job/format/format-recycle.yaml"

        private const val plainText = "auto-jvm/datasource/configured-delimited-format.yaml#PlainText"

        private val root = Path.of("build/format-worker")
        private const val recycledRows = 20_000
        private const val channelBatch = 1024
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val harness = ContentTestHarness()


    @Before
    fun setUp() {
        if (Files.exists(root)) {
            root.walk().sortedByDescending { it.nameCount }.forEach { Files.deleteIfExists(it) }
        }
        write("superset/a.csv", "name,value\nalpha,1\n")
        write("superset/b.csv", "name,value,extra\nbeta,2,x\n")
        write("column/c.csv", "group,value\nx,1\nx,2\ny,3\nx,4\n")
        write("sorted/s.csv", "group,value\nx,1\nx,2\ny,3\n")
        write("empty/e.csv", "name,value\n")
        write("record/r.csv", "name,value\nalpha,1\n\"b,c\",2\n")
        write("recycle/big.csv", bigCsv())
        BytesSinkWorker.reset()
    }


    @After
    fun tearDown() {
        harness.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun headerComesFromTheContractUnderCombinedColumns() {
        val validation = validate(superset)
        assertNull(validation.errorMessage)
        val contract = assertNotNull(validation.contract)
        assertIs<DataType.Opaque>(contract.payload().structural)
        assertEquals(Bytes::class.qualifiedName, contract.nativeByPath[DataTypePath.root]?.className?.asString())
        val metadata = assertNotNull(contract.metadata).structural
        assertEquals(
            listOf(ChunkMetadata.group, ChunkMetadata.format, FileValues.parent),
            metadata.fields.map { it.id.name })

        assertIs<Outcome.Success>(harness.run(superset))

        // Ungrouped, two files are one output: the header once, then the footer (empty for CSV), marked its end
        val chunks = BytesSinkWorker.chunks
        assertEquals(listOf("", "", ""), chunks.map { it.group })
        assertEquals(listOf("csv", "csv", "csv"), chunks.map { it.extension })
        assertEquals(listOf("a.csv", "b.csv", "b.csv"), chunks.map { it.file })
        assertEquals(listOf("name,value,extra\nalpha,1,\n", "beta,2,x\n", ""), chunks.map { it.text })
        assertEquals(listOf(false, false, true), chunks.map { it.ends })
    }


    @Test
    fun outputsFollowTheParentsName() {
        val outcome = run(superset, edited(superset, "groupBy", "parent.name"))

        assertIs<Outcome.Success>(outcome)
        val chunks = BytesSinkWorker.chunks
        assertEquals(listOf("a.csv", "b.csv", "a.csv", "b.csv"), chunks.map { it.group })
        assertEquals(
            listOf("name,value,extra\nalpha,1,\n", "name,value,extra\nbeta,2,x\n", "", ""),
            chunks.map { it.text })
        assertEquals(listOf(false, false, true, true), chunks.map { it.ends })
    }


    @Test
    fun groupsInAnyOrderAreEachOneOutputEndedAtTheEnd() {
        assertIs<Outcome.Success>(harness.run(column))

        val chunks = BytesSinkWorker.chunks
        assertEquals(listOf("x", "x", "y", "x", "x", "y"), chunks.map { it.group })
        assertEquals(
            listOf("group,value\nx,1\n", "x,2\n", "group,value\ny,3\n", "x,4\n", "", ""),
            chunks.map { it.text })
        assertEquals(listOf(false, false, false, false, true, true), chunks.map { it.ends })
    }


    @Test
    fun whenTheGroupChangesEachOutputEndsBeforeTheNextGroupsRecords() {
        val onChange = edited(column, "groupEnd", FormatWorker.groupEndOnChange)
        val sorted = edit(onChange, "files", "directory", root.resolve("sorted").toString())

        assertIs<Outcome.Success>(run(column, sorted))

        val chunks = BytesSinkWorker.chunks
        assertEquals(listOf("x", "x", "x", "y", "y"), chunks.map { it.group })
        assertEquals(listOf("group,value\nx,1\n", "x,2\n", "", "group,value\ny,3\n", ""), chunks.map { it.text })
        assertEquals(listOf(false, false, true, false, true), chunks.map { it.ends })
    }


    @Test
    fun whenTheGroupChangesAGroupThatComesBackFailsTheRun() {
        val failed = assertIs<Outcome.Failed>(run(column, edited(column, "groupEnd", FormatWorker.groupEndOnChange)))

        assertContains(failed.message,
            "Record 4: Group 'x' came back after 'y'; " +
                "set \"Write each file\" to \"At the end\", or sort by the group first")
    }


    @Test
    fun aGroupByThatDoesNotCompileIsRefusedBeforeRun() {
        val notation = edited(superset, "groupBy", "nosuch")

        assertContains(assertNotNull(validate(superset, notation).errorMessage), "Group by: ")

        assertIs<Outcome.Failed>(run(superset, notation))
        assertEquals(0, BytesSinkWorker.chunks.size)
    }


    @Test
    fun groupByGivesTheTypeOfItsValueBeforeRun() {
        val typeKey = JobFieldConventions.typeKey(AttributeName("groupBy"))

        val type = validate(superset, edited(superset, "groupBy", "parent.name")).details[typeKey]

        val contract = DataContract.ofExecutionValue(ExecutionValue.of(assertNotNull(type)))
        assertEquals(ScalarKind.Text, assertIs<DataType.Scalar>(contract.structural).kind)
        assertNull(validate(superset).details[typeKey])
    }


    @Test
    fun progressCountsRecordsAndBytes() {
        val engine = harness.start(superset)
        try {
            assertIs<Outcome.Success>(runBlocking {
                engine.resume()
                engine.await()
            })

            val format = formatLocation(superset)
            assertEquals(2L, harness.workerProgress(engine, format, JobOutputConventions.formatRecordsKey))
            assertEquals(
                BytesSinkWorker.chunks.sumOf { it.bytes.size }.toLong(),
                harness.workerProgress(engine, format, JobOutputConventions.formatBytesKey))
            assertNull(harness.workerProgress(engine, format, JobOutputConventions.formatGroupsOpenKey))
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun progressCountsGroupsWhenGrouped() {
        val engine = harness.start(column)
        try {
            assertIs<Outcome.Success>(runBlocking {
                engine.resume()
                engine.await()
            })

            val format = formatLocation(column)
            assertEquals(0L, harness.workerProgress(engine, format, JobOutputConventions.formatGroupsOpenKey))
            assertEquals(2L, harness.workerProgress(engine, format, JobOutputConventions.formatGroupsDoneKey))
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun noRecordsNoChunks() {
        assertIs<Outcome.Success>(harness.run(empty))

        assertEquals(0, BytesSinkWorker.chunks.size)
    }


    @Test
    fun formatWithoutWriterIsRefusedBeforeRun() {
        val notation = edited(superset, "format", plainText)

        assertContains(assertNotNull(validate(superset, notation).errorMessage), "Plain text cannot be written")

        val failed = assertIs<Outcome.Failed>(run(superset, notation))
        assertContains(failed.message, "Plain text cannot be written")
        assertEquals(0, BytesSinkWorker.chunks.size)
    }


    @Test
    fun columnsOutsideTheSchemaAreRefusedBeforeRun() {
        val error = assertNotNull(validate(schema).errorMessage)
        assertContains(error, "columns name, value, extra do not match its schema a, b")

        val failed = assertIs<Outcome.Failed>(harness.run(schema))
        assertContains(failed.message, "do not match its schema")
        assertEquals(0, BytesSinkWorker.chunks.size)
    }


    @Test
    fun recordTheFormatCannotWriteFailsByItsPosition() {
        assertNull(validate(record).errorMessage)

        val failed = assertIs<Outcome.Failed>(harness.run(record))

        assertContains(failed.message, "Record 2: ")
        assertContains(failed.message, "column 'name' contains the delimiter")
    }


    @Test
    fun chunksAreRecycled() {
        assertIs<Outcome.Success>(harness.run(recycle))

        // A chunk per record, then the footer's
        assertEquals(recycledRows + 1, BytesSinkWorker.chunks.size)
        assertEquals(bigCsv(), BytesSinkWorker.text())
        assertEquals(0, BytesSinkWorker.usedAfterRecycle.get())
        val created = BytesSinkWorker.slots.size
        println("WR3 Format pool: $created chunks created for $recycledRows records")
        assertTrue(created <= 4 * channelBatch, "$created chunks for $recycledRows records")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun write(relative: String, text: String) {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }


    private fun bigCsv(): String {
        val text = StringBuilder("id,value\n")
        for (i in 0 until recycledRows) {
            text.append(i).append(',').append("v").append(i * 7).append('\n')
        }
        return text.toString()
    }


    private fun formatLocation(document: String): ObjectLocation =
        worker(document, "format")


    private fun worker(document: String, name: String): ObjectLocation =
        ObjectLocation(DocumentPath.parse(document), ObjectPath.parse("main.workers/$name"))


    private fun edited(document: String, attribute: String, value: String): GraphNotation =
        ContentTestHarness.edit(AutoTestUtils.readNotation(), formatLocation(document), attribute, value)


    private fun edit(notation: GraphNotation, worker: String, attribute: String, value: String): GraphNotation =
        ContentTestHarness.edit(notation, worker(column, worker), attribute, value)


    private fun validate(document: String, notation: GraphNotation = AutoTestUtils.readNotation()) =
        harness.fresh().let { context ->
            val graphDefinition = AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful
            val validation = JobValidator.validateDetached(
                DocumentPath.parse(document), graphDefinition, context.notationMetadataReader,
                context.graphEnvironment, DesignReader().session(DesignReadBudget.editor))
            assertNotNull(validation.workerValidations[formatLocation(document).objectPath])
        }


    private fun run(document: String, notation: GraphNotation): Outcome {
        harness.fresh()
        val location = ContentTestHarness.jobLocation(document)
        val engine = harness.engine(harness.compile(location, notation), location)
        return try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
    }
}
