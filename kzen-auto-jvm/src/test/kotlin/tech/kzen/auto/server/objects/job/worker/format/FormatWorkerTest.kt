package tech.kzen.auto.server.objects.job.worker.format

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.data.design.DesignReadBudget
import tech.kzen.auto.server.data.design.DesignReader
import tech.kzen.auto.server.objects.job.JobValidator
import tech.kzen.auto.server.objects.job.worker.content.Bytes
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness
import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.engine.Outcome
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
 * `Format` (WR3, docs/plans/2026-10-07_format-and-write.md §4.4): each record becomes one pooled chunk of [Bytes]
 * named by the template, the chunk that starts an output carrying the header made from the input lane's columns;
 * what the format cannot write is refused before Run, and a record it cannot write fails the run by its position.
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
        assertEquals(listOf(FileValues.name, FileValues.parent), metadata.fields.map { it.id.name })

        assertIs<Outcome.Success>(harness.run(superset))

        val chunks = BytesSinkWorker.chunks
        assertEquals(listOf("output.csv", "output.csv"), chunks.map { it.name })
        assertEquals(listOf("a.csv", "b.csv"), chunks.map { it.file })
        assertEquals(listOf("name,value,extra\nalpha,1,\n", "beta,2,x\n"), chunks.map { it.text })
    }


    @Test
    fun outputsFollowTheParentsName() {
        val outcome = run(superset, edited(superset, "name", "\${parent.name}"))

        assertIs<Outcome.Success>(outcome)
        val chunks = BytesSinkWorker.chunks
        assertEquals(listOf("a.csv", "b.csv"), chunks.map { it.name })
        assertEquals(listOf("name,value,extra\nalpha,1,\n", "name,value,extra\nbeta,2,x\n"), chunks.map { it.text })
    }


    @Test
    fun outputsFollowAColumn() {
        assertIs<Outcome.Success>(harness.run(column))

        val chunks = BytesSinkWorker.chunks
        assertEquals(listOf("x.csv", "x.csv", "y.csv", "x.csv"), chunks.map { it.name })
        assertEquals(
            listOf("group,value\nx,1\n", "x,2\n", "group,value\ny,3\n", "group,value\nx,4\n"),
            chunks.map { it.text })
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

        assertEquals(recycledRows, BytesSinkWorker.chunks.size)
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
        ObjectLocation(DocumentPath.parse(document), ObjectPath.parse("main.workers/format"))


    private fun edited(document: String, attribute: String, value: String): GraphNotation =
        ContentTestHarness.edit(AutoTestUtils.readNotation(), formatLocation(document), attribute, value)


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
