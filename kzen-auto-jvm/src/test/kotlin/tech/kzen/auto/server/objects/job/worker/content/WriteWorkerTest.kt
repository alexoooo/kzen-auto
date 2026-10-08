package tech.kzen.auto.server.objects.job.worker.content

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.data.design.DesignReadBudget
import tech.kzen.auto.server.data.design.DesignReader
import tech.kzen.auto.server.objects.job.JobValidator
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.collected
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.edit
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.jobLocation
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.listFiles
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentList
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.io.path.walk
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * `Write` over chunks of `Bytes` (WR4, docs/plans/2026-10-07_format-and-write.md §4.5): each maximal run of chunks
 * with the same name is one file, published by atomic move with one `Written`; a name that returns is refused; a
 * failure or cancellation leaves no file and no temporary; and rows reach `Write` only through `Format`. A live
 * edit (WR8) keeps the open output, or, when it changes how the output is encoded or placed, discards it and starts
 * it again; either way the output is well-formed.
 */
class WriteWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val bytesDocument = "test/job/write/write-bytes.yaml"
        private const val stampDocument = "test/job/write/write-stamp.yaml"
        private const val rowsDocument = "test/job/write/write-rows.yaml"
        private const val readDocument = "test/job/write/write-read.yaml"

        private val root = Path.of("build/write-worker")
        private val inDirectory = root.resolve("in")
        private val outDirectory = root.resolve("out")

        private const val manyRows = 20_000
        private const val gateAtWrite = 100
        private const val waitSeconds = 30L
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val harness = ContentTestHarness()


    @Before
    fun setUp() {
        if (Files.exists(root)) {
            root.walk().sortedByDescending { it.nameCount }.forEach { Files.deleteIfExists(it) }
        }
        Files.createDirectories(inDirectory)
        WriteWorker.encoderInterceptor = null
        ChunkStampWorker.reset()
    }


    @After
    fun tearDown() {
        WriteWorker.encoderInterceptor = null
        harness.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun twoFilesBecomeOneOutput() {
        writeInput("a.csv", "name,value\nalpha,1\nbeta,2\n")
        writeInput("b.csv", "name,value\ngamma,3\n")
        assertNull(validate(bytesDocument).errorMessage)

        val outcome = harness.run(bytesDocument)

        assertEquals(listOf("output.csv"), writtenNames(outcome))
        assertEquals(setOf("output.csv"), listFiles(outDirectory))
        assertEquals("name,value\nalpha,1\nbeta,2\ngamma,3\n", Files.readString(outDirectory.resolve("output.csv")))
    }


    @Test
    fun eachSourceFileIsItsOwnOutputWithOneWrittenEach() {
        writeInput("a.csv", "name,value\nalpha,1\nbeta,2\n")
        writeInput("b.csv", "name,value\ngamma,3\n")

        val outcome = run(bytesDocument, edited(bytesDocument, "format", "name", "\${parent.name}"))

        val written = collected(outcome).map { it as Written }
        assertEquals(listOf("a.csv", "b.csv"), written.map { it.name })
        assertEquals(setOf("a.csv", "b.csv"), listFiles(outDirectory))
        assertEquals("name,value\nalpha,1\nbeta,2\n", Files.readString(outDirectory.resolve("a.csv")))
        assertEquals("name,value\ngamma,3\n", Files.readString(outDirectory.resolve("b.csv")))
        assertEquals(written.map { Files.size(outDirectory.resolve(it.name)) }, written.map { it.size })
    }


    @Test
    fun gzipCompressesTheOutputAndAddsItsExtension() {
        writeInput("a.csv", "name,value\nalpha,1\n")
        writeInput("b.csv", "name,value\ngamma,3\n")

        val outcome = run(bytesDocument, edited(bytesDocument, "write", "compression", WriteWorker.compressionGzip))

        assertEquals(listOf("output.csv"), writtenNames(outcome))
        assertEquals(setOf("output.csv.gz"), listFiles(outDirectory))
        assertEquals("name,value\nalpha,1\ngamma,3\n", inflate(outDirectory.resolve("output.csv.gz")))
    }


    @Test
    fun aNameThatReturnsIsRefusedByName() {
        writeInput("c.csv", "group,value\nx,1\nx,2\ny,3\nx,4\n")

        val outcome = run(bytesDocument, edited(bytesDocument, "format", "name", "\${group}\${extension}"))

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "'x.csv' was already written in this run; sort by what the name is built from first")
        // The outputs that ended before the refusal are published; nothing else, no temporary
        assertEquals(setOf("x.csv", "y.csv"), listFiles(outDirectory))
        assertEquals("group,value\nx,1\nx,2\n", Files.readString(outDirectory.resolve("x.csv")))
    }


    @Test
    fun twoNamesWrittenToOneFileAreRefused() {
        writeSources()

        val named = edited(bytesDocument, "format", "name", "\${parent.name}")
        val outcome = run(bytesDocument, edit(named, worker(bytesDocument, "write"), "name", "merged.csv"))

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "'b.csv' would replace 'a.csv', written to the same file in this run")
        assertEquals(setOf("merged.csv"), listFiles(outDirectory))
        assertEquals("name,value\nalpha,1\n", Files.readString(outDirectory.resolve("merged.csv")))
    }


    @Test
    fun anEmptyNameIsRefused() {
        writeInput("c.csv", "group,value\n,1\n")

        // With gzip, an empty name would otherwise publish the hidden file ".gz"
        val notation = edited(bytesDocument, "format", "name", "\${group}")
        val outcome = run(bytesDocument, edit(notation, worker(bytesDocument, "write"), "compression", WriteWorker.compressionGzip))

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Write cannot write bytes with an empty name")
        assertEquals(emptySet(), listFiles(outDirectory))
    }


    @Test
    fun anExistingDestinationFailsTheRun() {
        writeSources()
        writeOutput("a.csv", "old\n")

        val outcome = run(bytesDocument, perSource(WriteWorker.existingFail))

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Destination for 'a.csv' already exists")
        assertEquals(setOf("a.csv"), listFiles(outDirectory))
        assertEquals("old\n", Files.readString(outDirectory.resolve("a.csv")))
    }


    @Test
    fun anExistingDestinationIsReplaced() {
        writeSources()
        writeOutput("a.csv", "old\n")

        val outcome = run(bytesDocument, perSource(WriteWorker.existingReplace))

        assertEquals(listOf("a.csv", "b.csv"), writtenNames(outcome))
        assertEquals(setOf("a.csv", "b.csv"), listFiles(outDirectory))
        assertEquals("name,value\nalpha,1\n", Files.readString(outDirectory.resolve("a.csv")))
    }


    @Test
    fun anExistingDestinationSkipsItsWholeOutput() {
        writeSources()
        writeOutput("a.csv", "old\n")

        val outcome = run(bytesDocument, perSource(WriteWorker.existingSkip))

        assertEquals(listOf("b.csv"), writtenNames(outcome))
        assertEquals(setOf("a.csv", "b.csv"), listFiles(outDirectory))
        assertEquals("old\n", Files.readString(outDirectory.resolve("a.csv")))
        assertEquals("name,value\ngamma,3\n", Files.readString(outDirectory.resolve("b.csv")))
    }


    @Test
    fun aFailureMidOutputDeletesTheTemporary() {
        writeInput("big.csv", numberedCsv(manyRows))
        val writes = AtomicInteger()
        val sawTemporary = AtomicBoolean()
        WriteWorker.encoderInterceptor = { encoded ->
            object: FilterOutputStream(encoded) {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (writes.incrementAndGet() == gateAtWrite) {
                        sawTemporary.set(listFiles(outDirectory).any { it.endsWith(".part") })
                        throw IOException("injected write failure")
                    }
                    out.write(b, off, len)
                }
            }
        }

        val outcome = harness.run(bytesDocument)

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "injected write failure")
        assertTrue(sawTemporary.get(), "the output should have been open in a temporary")
        assertEquals(emptySet(), listFiles(outDirectory))
    }


    @Test
    fun cancellationMidOutputPublishesNothing() {
        writeInput("big.csv", numberedCsv(manyRows))
        val gate = WriteGate()
        WriteWorker.encoderInterceptor = gate::intercept

        val engine = harness.start(bytesDocument)
        val outcome = try {
            runBlocking {
                val terminal = async { engine.await() }
                engine.resume()
                gate.awaitReached()
                engine.cancel()
                gate.release()
                terminal.await()
            }
        }
        finally {
            gate.release()
            engine.close()
        }

        assertIs<Outcome.Cancelled>(outcome)
        assertEquals(emptySet(), listFiles(outDirectory))
    }


    @Test
    fun aLiveEditToExistingKeepsTheOpenOutput() {
        val text = numberedCsv(manyRows)
        writeInput("big.csv", text)

        val outcome = liveEdit(bytesDocument) { notation ->
            edit(notation, worker(bytesDocument, "write"), "existing", WriteWorker.existingReplace)
        }

        // One header, every row once, one Written, no temporary
        assertEquals(listOf("output.csv"), writtenNames(outcome))
        assertEquals(setOf("output.csv"), listFiles(outDirectory))
        assertEquals(text, Files.readString(outDirectory.resolve("output.csv")))
    }


    @Test
    fun aLiveEditToFormatsNameThatResolvesTheSameKeepsTheOpenOutput() {
        val text = numberedCsv(manyRows)
        writeInput("big.csv", text)

        val outcome = liveEdit(bytesDocument) { notation ->
            edit(notation, worker(bytesDocument, "format"), "name", "output.csv")
        }

        assertEquals(listOf("output.csv"), writtenNames(outcome))
        assertEquals(setOf("output.csv"), listFiles(outDirectory))
        assertEquals(text, Files.readString(outDirectory.resolve("output.csv")))
    }


    @Test
    fun aLiveEditToFormatsNameEndsTheOpenOutputWhereTheNameChanges() {
        writeInput("big.csv", numberedCsv(manyRows))

        val outcome = liveEdit(bytesDocument) { notation ->
            edit(notation, worker(bytesDocument, "format"), "name", "edited\${extension}")
        }

        // As any change of name: the first output ends at the edit, the next starts with its header; each row once
        assertEquals(listOf("output.csv", "edited.csv"), writtenNames(outcome))
        assertEquals(setOf("output.csv", "edited.csv"), listFiles(outDirectory))
        val before = Files.readString(outDirectory.resolve("output.csv"))
        val after = Files.readString(outDirectory.resolve("edited.csv"))
        assertTrue(before.startsWith("id,value\n0,v0\n"))
        assertTrue(after.startsWith("id,value\n"))
        assertEquals(numberedCsv(manyRows), before + after.removePrefix("id,value\n"))
    }


    @Test
    fun aLiveEditToWritesCompressionDiscardsTheOpenOutputAndStartsItAgain() {
        writeInput("big.csv", numberedCsv(manyRows))

        val outcome = liveEdit(bytesDocument) { notation ->
            edit(notation, worker(bytesDocument, "write"), "compression", WriteWorker.compressionGzip)
        }

        // The uncompressed output is never published; the compressed one holds the header and the rows after the edit
        assertEquals(listOf("output.csv"), writtenNames(outcome))
        assertEquals(setOf("output.csv.gz"), listFiles(outDirectory))
        assertRowsAfterTheEdit("id,value", ",", inflate(outDirectory.resolve("output.csv.gz")))
    }


    @Test
    fun aLiveEditToFormatsColumnsDiscardsTheOpenOutputAndStartsItAgain() {
        writeInput("big.csv", numberedCsv(manyRows))

        val outcome = liveEdit(bytesDocument) { notation ->
            edit(notation, worker(bytesDocument, "format"), "columns",
                ListAttributeNotation(listOf(ScalarAttributeNotation("value")).toPersistentList()))
        }

        // Same name: the output restarts in place, with the new header and only the rows after the edit
        assertEquals(listOf("output.csv"), writtenNames(outcome))
        assertEquals(setOf("output.csv"), listFiles(outDirectory))
        val lines = Files.readString(outDirectory.resolve("output.csv")).lines().dropLast(1)
        assertEquals("value", lines.first())
        val values = lines.drop(1).map { it.removePrefix("v").toInt() }
        assertTrue(values.first() > 0, "the rows written before the edit should be gone")
        assertEquals((values.first() until manyRows).toList(), values)
    }


    @Test
    fun aLiveEditToFormatsFormatDiscardsTheOpenOutputEvenUnderANewName() {
        writeInput("big.csv", numberedCsv(manyRows))

        val outcome = liveEdit(bytesDocument) { notation ->
            edit(notation, worker(bytesDocument, "format"), "format",
                "auto-jvm/datasource/configured-delimited-format.yaml#ConfiguredTsv")
        }

        // `output${extension}` becomes output.tsv; the CSV output cut by the edit is not published
        assertEquals(listOf("output.tsv"), writtenNames(outcome))
        assertEquals(setOf("output.tsv"), listFiles(outDirectory))
        assertRowsAfterTheEdit("id\tvalue", "\t", Files.readString(outDirectory.resolve("output.tsv")))
    }


    @Test
    fun chunksCarriedAcrossALiveEditAreWrittenWhileHeldAndNeverAfterRecycle() {
        val text = numberedCsv(manyRows)
        writeInput("big.csv", text)

        val outcome = liveEdit(
            stampDocument,
            ChunkStampWorker::intercept,
            atEdit = {
                assertTrue(ChunkStampWorker.stamped.get() > ChunkStampWorker.checked.get(),
                    "chunks should be in flight to Write at the edit")
            }
        ) { notation ->
            edit(notation, worker(stampDocument, "write"), "existing", WriteWorker.existingReplace)
        }

        assertEquals(listOf("output.csv"), writtenNames(outcome))
        assertEquals(setOf("output.csv"), listFiles(outDirectory))
        assertEquals(text, Files.readString(outDirectory.resolve("output.csv")))
        assertEquals(manyRows, ChunkStampWorker.checked.get())
        assertEquals(0, ChunkStampWorker.unstamped.get())
        assertEquals(0, ChunkStampWorker.usedAfterRecycle.get())
    }


    @Test
    fun rowsWithoutFormatAreRefusedBeforeRun() {
        writeInput("a.csv", "name,value\nalpha,1\n")

        val validation = validate(rowsDocument)
        assertEquals(WriteWorker.inputRequirement, validation.errorMessage)

        val failed = assertIs<Outcome.Failed>(harness.run(rowsDocument))
        assertContains(failed.message, WriteWorker.inputRequirement)
        assertEquals(emptySet(), listFiles(outDirectory))
    }


    @Test
    fun formattedRowsReadBackAsTheSameRows() {
        writeInput("a.csv", "id,text,amount\n1,plain,1.5\n2,\"comma, inside\",2\n3,\"quote \"\"q\"\"\",3\n")
        writeInput("b.csv", "id,text,amount\n4,\"line\nbreak\",4\n5,\" padded \",-5\n")

        assertIs<Outcome.Success>(harness.run(bytesDocument))
        val written = collected(run(readDocument, AutoTestUtils.readNotation()))
        val source = collected(run(readDocument, edited(readDocument, "files", "directory", inDirectory.toString())))

        assertEquals(5, source.size)
        assertEquals(source, written)
    }


    @Test
    fun chunksAreWrittenWhileHeldAndNeverAfterRecycle() {
        val text = numberedCsv(manyRows)
        writeInput("big.csv", text)
        WriteWorker.encoderInterceptor = ChunkStampWorker::intercept

        assertEquals(listOf("output.csv"), writtenNames(harness.run(stampDocument)))

        assertEquals(text, Files.readString(outDirectory.resolve("output.csv")))
        assertEquals(manyRows, ChunkStampWorker.checked.get())
        assertEquals(0, ChunkStampWorker.unstamped.get())
        assertEquals(0, ChunkStampWorker.usedAfterRecycle.get())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun writeInput(name: String, text: String) {
        Files.writeString(inDirectory.resolve(name), text)
    }


    private fun writeOutput(name: String, text: String) {
        Files.createDirectories(outDirectory)
        Files.writeString(outDirectory.resolve(name), text)
    }


    private fun writeSources() {
        writeInput("a.csv", "name,value\nalpha,1\n")
        writeInput("b.csv", "name,value\ngamma,3\n")
    }


    private fun numberedCsv(rows: Int): String {
        val text = StringBuilder("id,value\n")
        for (i in 0 until rows) {
            text.append(i).append(",v").append(i).append('\n')
        }
        return text.toString()
    }


    /**
     * Runs [document] up to its [gateAtWrite]-th chunk write, pauses there with the output open, edits the notation
     * with [edit], and runs the edited Job to the end; [intercept] wraps Write's encoder under the gate, and [atEdit]
     * looks at the paused run.
     */
    private fun liveEdit(
        document: String,
        intercept: (OutputStream) -> OutputStream = { it },
        atEdit: () -> Unit = {},
        edit: (GraphNotation) -> GraphNotation
    ): Outcome {
        val notation = AutoTestUtils.readNotation()
        val location = jobLocation(document)
        harness.fresh()
        val base = harness.compile(location, notation)
        val edited = harness.compile(location, edit(notation))
        val gate = WriteGate()
        WriteWorker.encoderInterceptor = { encoded -> gate.intercept(intercept(encoded)) }

        val engine = harness.engine(base, location)
        return try {
            engine.resume()
            gate.awaitReached()
            engine.pause()
            gate.release()
            engine.awaitQuiescent()
            assertTrue(listFiles(outDirectory).single().endsWith(".part"), "the output should be open at the edit")
            atEdit()
            engine.migrate(edited, paused = false)
            runBlocking { engine.await() }
        }
        finally {
            gate.release()
            engine.close()
        }
    }


    /** [text] is one [header] and then the numbered rows from some row after the first to the last, each once. */
    private fun assertRowsAfterTheEdit(header: String, delimiter: String, text: String) {
        val lines = text.lines().dropLast(1)
        assertEquals(header, lines.first())
        val ids = lines.drop(1).map { line ->
            val (id, value) = line.split(delimiter)
            assertEquals("v$id", value)
            id.toInt()
        }
        assertTrue(ids.first() > 0, "the rows written before the edit should be gone")
        assertEquals((ids.first() until manyRows).toList(), ids)
    }


    private fun inflate(path: Path): String =
        GZIPInputStream(Files.newInputStream(path)).use { it.readAllBytes().decodeToString() }


    private fun writtenNames(outcome: Outcome): List<String> =
        collected(outcome).map { (it as Written).name }


    private fun worker(document: String, name: String): ObjectLocation =
        ObjectLocation(DocumentPath.parse(document), ObjectPath.parse("main.workers/$name"))


    private fun edited(document: String, worker: String, attribute: String, value: String): GraphNotation =
        edit(AutoTestUtils.readNotation(), worker(document, worker), attribute, value)


    private fun perSource(existing: String): GraphNotation {
        val named = edited(bytesDocument, "format", "name", "\${parent.name}")
        return edit(named, worker(bytesDocument, "write"), "existing", existing)
    }


    private fun validate(document: String) =
        harness.fresh().let { context ->
            val graphDefinition = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation()).transitiveSuccessful
            val validation = JobValidator.validateDetached(
                DocumentPath.parse(document), graphDefinition, context.notationMetadataReader,
                context.graphEnvironment, DesignReader().session(DesignReadBudget.editor))
            assertNotNull(validation.workerValidations[worker(document, "write").objectPath])
        }


    private fun run(document: String, notation: GraphNotation): Outcome {
        harness.fresh()
        val location = jobLocation(document)
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


    /** Holds Write inside the first output, at its [gateAtWrite]-th chunk, until released; deaf to interrupts. */
    private class WriteGate {
        private val writes = AtomicInteger()
        private val reached = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun intercept(encoded: OutputStream): OutputStream =
            object: FilterOutputStream(encoded) {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (writes.incrementAndGet() == gateAtWrite) {
                        reached.countDown()
                        awaitRelease()
                    }
                    out.write(b, off, len)
                }
            }

        fun awaitReached() {
            check(reached.await(waitSeconds, TimeUnit.SECONDS)) { "Write never reached chunk $gateAtWrite" }
        }

        fun release() {
            released.countDown()
        }

        private fun awaitRelease() {
            var interrupted = false
            while (true) {
                try {
                    check(released.await(waitSeconds, TimeUnit.SECONDS)) { "the gate was never released" }
                    break
                }
                catch (_: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
