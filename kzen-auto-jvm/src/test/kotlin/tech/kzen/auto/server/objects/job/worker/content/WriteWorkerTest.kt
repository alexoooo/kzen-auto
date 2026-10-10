package tech.kzen.auto.server.objects.job.worker.content

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobFieldConventions
import tech.kzen.auto.common.objects.document.job.JobOutputConventions
import tech.kzen.auto.server.data.design.DesignReadBudget
import tech.kzen.auto.server.data.design.DesignReader
import tech.kzen.auto.server.objects.job.JobValidator
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.collected
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.edit
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.jobLocation
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.listFiles
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.auto.server.util.hangGuardMillis
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.server.exec.engine.RunEngine
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
 * `Write` over chunks of `Bytes` (WR4, docs/plans/2026-10-07_format-and-write.md §4.5; FG2,
 * docs/plans/2026-10-09_format-groups-and-write-naming.md §4.1, §4.4): each group's chunks are one file, any number
 * open at once, published by atomic move with one `Written` when its producer marks its end; two groups written to
 * one file are refused; a failure or cancellation leaves no file and no temporary; and rows reach `Write` only
 * through `Format`. Where a file goes is its sub-folder and name templates, every inserted value made safe. A live
 * edit (WR8) keeps the open outputs, or, when it changes how they are encoded or placed, discards each and starts it
 * again; either way every output is well-formed.
 */
class WriteWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val bytesDocument = "test/job/write/write-bytes.yaml"
        private const val stampDocument = "test/job/write/write-stamp.yaml"
        private const val rowsDocument = "test/job/write/write-rows.yaml"
        private const val readDocument = "test/job/write/write-read.yaml"
        private const val unendedDocument = "test/job/write/write-unended.yaml"

        private val root = Path.of("build/write-worker")
        private val inDirectory = root.resolve("in")
        private val outDirectory = root.resolve("out")

        private const val manyRows = 20_000
        private const val gateAtWrite = 100
        // Past the first flush of Write's 128 KiB stream buffer, so bytes have reached the file
        private const val gateAfterFirstFlush = 15_000
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val harness = ContentTestHarness()
    private val write = worker(bytesDocument, "write")


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

        val outcome = run(bytesDocument, perSource())

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

        assertEquals(listOf("output.csv.gz"), writtenNames(outcome))
        assertEquals(setOf("output.csv.gz"), listFiles(outDirectory))
        assertEquals("name,value\nalpha,1\ngamma,3\n", inflate(outDirectory.resolve("output.csv.gz")))
    }


    @Test
    fun groupsInAnyOrderAreEachOneWholeFile() {
        writeInput("c.csv", "group,value\nx,1\nx,2\ny,3\nx,4\n")

        val outcome = run(bytesDocument, edited(bytesDocument, "format", "groupBy", "group"))

        // Each named by the default for bytes, its group then the extension
        assertEquals(listOf("x.csv", "y.csv"), writtenNames(outcome))
        assertEquals(setOf("x.csv", "y.csv"), listFiles(outDirectory))
        assertEquals("group,value\nx,1\nx,2\nx,4\n", Files.readString(outDirectory.resolve("x.csv")))
        assertEquals("group,value\ny,3\n", Files.readString(outDirectory.resolve("y.csv")))
    }


    @Test
    fun whenTheGroupChangesEachFileIsWholeAsItEnds() {
        writeInput("c.csv", "group,value\nx,1\nx,2\ny,3\n")

        val outcome = run(bytesDocument, onChange("group"))

        assertEquals(listOf("x.csv", "y.csv"), writtenNames(outcome))
        assertEquals("group,value\nx,1\nx,2\n", Files.readString(outDirectory.resolve("x.csv")))
        assertEquals("group,value\ny,3\n", Files.readString(outDirectory.resolve("y.csv")))
    }


    @Test
    fun aGroupThatComesBackWhenTheGroupChangesFailsTheRun() {
        writeInput("c.csv", "group,value\nx,1\nx,2\ny,3\nx,4\n")

        val outcome = run(bytesDocument, onChange("group"))

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Group 'x' came back after 'y'")
        // The run stops as it fails: x's file may have been published by then, whole; y's never is, nor a temporary
        val files = listFiles(outDirectory)
        assertTrue(files.isEmpty() || files == setOf("x.csv"), "$files")
        if (files.isNotEmpty()) {
            assertEquals("group,value\nx,1\nx,2\n", Files.readString(outDirectory.resolve("x.csv")))
        }
    }


    @Test
    fun twoGroupsWrittenToOneFileAreRefused() {
        writeSources()

        val outcome = run(bytesDocument, edit(perSource(), write, "name", "merged.csv"))

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "'merged.csv' is written by group 'a.csv' and again by group 'b.csv'")
        assertEquals(emptySet(), listFiles(outDirectory))
    }


    @Test
    fun anEmptyNameIsRefused() {
        writeInput("c.csv", "group,value\n,1\n")

        val grouped = edited(bytesDocument, "format", "groupBy", "group")
        val outcome = run(bytesDocument, edit(grouped, write, "name", "\$group"))

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "File name resolved to ''; '' cannot name a file")
        assertEquals(emptySet(), listFiles(outDirectory))
    }


    @Test
    fun anOutputWhoseEndNeverComesFailsTheRunByName() {
        writeSources()

        val outcome = harness.run(unendedDocument)

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message,
            "Input ended before the end of 'output.csv'; its producer marks the end of each output")
        assertEquals(emptySet(), listFiles(outDirectory))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun insertedValuesAreMadeSafeInAFolderAndAName() {
        writeInput("c.csv", "group,value\n\"a/b:c. \",1\nCON,2\nnul.csv,3\n")

        val grouped = edited(bytesDocument, "format", "groupBy", "group")
        val outcome = run(bytesDocument, edit(edit(grouped, write, "folder", "\$group"), write, "name", "\$group"))

        assertEquals(listOf("a_b_c/a_b_c", "_CON/_CON", "_nul.csv/_nul.csv"), writtenNames(outcome))
        assertEquals(setOf("a_b_c/a_b_c", "_CON/_CON", "_nul.csv/_nul.csv"), listFiles(outDirectory))
    }


    @Test
    fun aSlashTypedInTheSubFolderMakesFolders() {
        writeSources()

        val notation = edited(bytesDocument, "write", "folder", "x/y")
        assertNull(validate(bytesDocument, notation).errorMessage)

        assertEquals(listOf("x/y/output.csv"), writtenNames(run(bytesDocument, notation)))
        assertEquals(setOf("x/y/output.csv"), listFiles(outDirectory))
    }


    @Test
    fun aSlashTypedInTheNameIsRefusedBeforeRun() {
        writeSources()

        val notation = edited(bytesDocument, "write", "name", "x/output.csv")

        assertEquals(
            "File name: '/' would make a folder; put folders in Sub-folder",
            validate(bytesDocument, notation).errorMessage)
        assertIs<Outcome.Failed>(run(bytesDocument, notation))
        assertEquals(emptySet(), listFiles(outDirectory))
    }


    @Test
    fun aColonTypedInTheNameIsRefusedBeforeRun() {
        writeSources()

        val notation = edited(bytesDocument, "write", "name", "a:b.csv")

        assertEquals("File name: ':' cannot be in a path", validate(bytesDocument, notation).errorMessage)
    }


    @Test
    fun aNameOfDotDotFailsTheRunByName() {
        writeSources()

        val outcome = run(bytesDocument, edited(bytesDocument, "write", "name", ".."))

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "File name resolved to '..'; '..' cannot name a file")
        assertEquals(emptySet(), listFiles(outDirectory))
    }


    @Test
    fun aBraceOrSlashInsideAnExpressionsStringIsPartOfTheExpression() {
        writeSources()

        val notation = edited(bytesDocument, "write", "name", "\${\"a}b/c\"}.txt")
        assertNull(validate(bytesDocument, notation).errorMessage)

        // The '/' is in a value the expression computes, so it is made safe like any other
        assertEquals(listOf("a}b_c.txt"), writtenNames(run(bytesDocument, notation)))
    }


    @Test
    fun aBlankFileNameStandsForTheDefaultForBytesBeforeRun() {
        val defaultKey = JobFieldConventions.defaultKey(AttributeName("name"))

        assertEquals(WriteWorker.bytesDefaultName, validate(bytesDocument).details[defaultKey])
    }


    @Test
    fun anExpressionThatDoesNotCompileIsRefusedBeforeRun() {
        writeSources()

        val notation = edited(bytesDocument, "write", "name", "\${nosuch}.csv")

        assertContains(assertNotNull(validate(bytesDocument, notation).errorMessage), "File name: ")
        assertIs<Outcome.Failed>(run(bytesDocument, notation))
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
            edit(notation, write, "existing", WriteWorker.existingReplace)
        }

        // One header, every row once, one Written, no temporary
        assertEquals(listOf("output.csv"), writtenNames(outcome))
        assertEquals(setOf("output.csv"), listFiles(outDirectory))
        assertEquals(text, Files.readString(outDirectory.resolve("output.csv")))
    }


    @Test
    fun aLiveEditToWritesNameDiscardsTheOpenOutputAndStartsItUnderTheNewName() {
        writeInput("big.csv", numberedCsv(manyRows))

        val outcome = liveEdit(bytesDocument) { notation ->
            edit(notation, write, "name", "edited\${extension}")
        }

        // The output cut by the edit is not published; the renamed one starts behind the header it had
        assertEquals(listOf("edited.csv"), writtenNames(outcome))
        assertEquals(setOf("edited.csv"), listFiles(outDirectory))
        assertRowsAfterTheEdit("id,value", ",", Files.readString(outDirectory.resolve("edited.csv")))
    }


    @Test
    fun timeIsTheRunsStartAcrossALiveEdit() {
        writeInput("big.csv", numberedCsv(manyRows))
        val timed = edited(bytesDocument, "write", "folder", "\$time")

        var folderAtEdit: String? = null
        val outcome = liveEdit(
            bytesDocument,
            notation = timed,
            atEdit = {
                folderAtEdit = listFiles(outDirectory).single().substringBefore('/')
                // Into the next second, so a time taken again would differ
                Thread.sleep(1_100)
            }
        ) { notation ->
            edit(notation, write, "compression", WriteWorker.compressionGzip)
        }

        // The output starts again after the edit, in the folder of the run's start
        val folder = assertNotNull(folderAtEdit)
        assertEquals(listOf("$folder/output.csv.gz"), writtenNames(outcome))
        assertEquals(setOf("$folder/output.csv.gz"), listFiles(outDirectory))
    }


    @Test
    fun aLiveEditToWritesCompressionDiscardsTheOpenOutputAndStartsItAgain() {
        writeInput("big.csv", numberedCsv(manyRows))

        var discardsReported: Any? = null
        val outcome = liveEdit(
            bytesDocument,
            atEnd = { engine ->
                discardsReported = harness.workerProgress(
                    engine, write, JobOutputConventions.writeDiscardsKey)
            }
        ) { notation ->
            edit(notation, write, "compression", WriteWorker.compressionGzip)
        }

        // The uncompressed output is never published; the compressed one holds the header and the rows after the edit
        assertEquals(listOf("output.csv.gz"), writtenNames(outcome))
        assertEquals(setOf("output.csv.gz"), listFiles(outDirectory))
        assertRowsAfterTheEdit("id,value", ",", inflate(outDirectory.resolve("output.csv.gz")))
        // ... and the discard is reported, once, for the one output
        assertDiscarded(1, discardsReported)
    }


    @Test
    fun progressNamesTheOpenFileThenCountsThePublishedOne() {
        writeInput("big.csv", numberedCsv(manyRows))
        val gate = WriteGate(gateAfterFirstFlush)
        WriteWorker.encoderInterceptor = gate::intercept
        val write = write

        val engine = harness.start(bytesDocument)
        try {
            engine.resume()
            gate.awaitReached()
            engine.pause()
            gate.release()
            engine.awaitQuiescent()

            assertEquals(1L, harness.workerProgress(engine, write, JobOutputConventions.writeOpenKey))
            assertEquals("output.csv", harness.workerProgress(engine, write, JobOutputConventions.writeOpenNameKey))
            assertEquals(0L, harness.workerProgress(engine, write, JobOutputConventions.writeFilesKey))
            val midway = assertIs<Long>(harness.workerProgress(engine, write, JobOutputConventions.writeBytesKey))
            assertTrue(midway > 0, "bytes should be on disk while the output is open")

            engine.resume()
            assertIs<Outcome.Success>(runBlocking { engine.await() })

            val size = Files.size(outDirectory.resolve("output.csv"))
            assertTrue(midway < size)
            assertEquals(0L, harness.workerProgress(engine, write, JobOutputConventions.writeOpenKey))
            assertNull(harness.workerProgress(engine, write, JobOutputConventions.writeOpenNameKey))
            assertEquals(1L, harness.workerProgress(engine, write, JobOutputConventions.writeFilesKey))
            assertEquals(size, harness.workerProgress(engine, write, JobOutputConventions.writeBytesKey))
            assertNull(harness.workerProgress(engine, write, JobOutputConventions.writeDiscardsKey))
        }
        finally {
            gate.release()
            engine.close()
        }
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

        // The default name's extension becomes .tsv; the CSV output cut by the edit is not published
        assertEquals(listOf("output.tsv"), writtenNames(outcome))
        assertEquals(setOf("output.tsv"), listFiles(outDirectory))
        assertRowsAfterTheEdit("id\tvalue", "\t", Files.readString(outDirectory.resolve("output.tsv")))
    }


    @Test
    fun aGroupedLiveEditToExistingKeepsEveryOpenOutput() {
        writeInput("big.csv", groupedCsv(manyRows))

        val outcome = liveEdit(bytesDocument, notation = edited(bytesDocument, "format", "groupBy", "group")) { notation ->
            edit(notation, write, "existing", WriteWorker.existingReplace)
        }

        assertEquals(listOf("0.csv", "1.csv"), writtenNames(outcome))
        assertEquals(groupCsv(manyRows, 0), Files.readString(outDirectory.resolve("0.csv")))
        assertEquals(groupCsv(manyRows, 1), Files.readString(outDirectory.resolve("1.csv")))
    }


    @Test
    fun aGroupedLiveEditToFormatsColumnsDiscardsEveryOpenOutput() {
        writeInput("big.csv", groupedCsv(manyRows))

        var discardsReported: Any? = null
        val outcome = liveEdit(
            bytesDocument,
            notation = edited(bytesDocument, "format", "groupBy", "group"),
            atEnd = { engine ->
                discardsReported = harness.workerProgress(engine, write, JobOutputConventions.writeDiscardsKey)
            }
        ) { notation ->
            edit(notation, worker(bytesDocument, "format"), "columns", ListAttributeNotation(
                listOf(ScalarAttributeNotation("id"), ScalarAttributeNotation("value")).toPersistentList()))
        }

        // Each output starts again with the new header and holds only its rows after the edit
        assertEquals(listOf("0.csv", "1.csv"), writtenNames(outcome))
        assertRowsAfterTheEdit("id,value", ",", Files.readString(outDirectory.resolve("0.csv")), 0)
        assertRowsAfterTheEdit("id,value", ",", Files.readString(outDirectory.resolve("1.csv")), 1)
        assertDiscarded(2, discardsReported)
    }


    @Test
    fun aGroupedLiveEditToWritesCompressionStartsEveryOutputAgainBehindItsHeader() {
        writeInput("big.csv", groupedCsv(manyRows))

        var discardsReported: Any? = null
        val outcome = liveEdit(
            bytesDocument,
            notation = edited(bytesDocument, "format", "groupBy", "group"),
            atEnd = { engine ->
                discardsReported = harness.workerProgress(engine, write, JobOutputConventions.writeDiscardsKey)
            }
        ) { notation ->
            edit(notation, write, "compression", WriteWorker.compressionGzip)
        }

        assertEquals(listOf("0.csv.gz", "1.csv.gz"), writtenNames(outcome))
        assertEquals(setOf("0.csv.gz", "1.csv.gz"), listFiles(outDirectory))
        assertRowsAfterTheEdit("id,group,value", ",", inflate(outDirectory.resolve("0.csv.gz")), 0)
        assertRowsAfterTheEdit("id,group,value", ",", inflate(outDirectory.resolve("1.csv.gz")), 1)
        assertDiscarded(2, discardsReported)
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
        // Each record's chunk, then the footer's (empty for CSV)
        assertEquals(manyRows + 1, ChunkStampWorker.checked.get())
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
        assertEquals(manyRows + 1, ChunkStampWorker.checked.get())
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


    /** Numbered rows in two groups, `0` and `1`, alternating. */
    private fun groupedCsv(rows: Int): String {
        val text = StringBuilder("id,group,value\n")
        for (i in 0 until rows) {
            text.append(i).append(',').append(i % 2).append(",v").append(i).append('\n')
        }
        return text.toString()
    }


    /** The whole output of [group] of [groupedCsv]. */
    private fun groupCsv(rows: Int, group: Int): String {
        val text = StringBuilder("id,group,value\n")
        for (i in group until rows step 2) {
            text.append(i).append(',').append(group).append(",v").append(i).append('\n')
        }
        return text.toString()
    }


    /**
     * Runs [document] as [notation] has it up to its [gateAtWrite]-th chunk write, pauses there with its outputs
     * open, edits the notation with [edit], and runs the edited Job to the end; [intercept] wraps Write's encoder
     * under the gate, [atEdit] looks at the paused run, and [atEnd] at the finished one.
     */
    private fun liveEdit(
        document: String,
        intercept: (OutputStream) -> OutputStream = { it },
        notation: GraphNotation = AutoTestUtils.readNotation(),
        atEdit: () -> Unit = {},
        atEnd: (RunEngine) -> Unit = {},
        edit: (GraphNotation) -> GraphNotation
    ): Outcome {
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
            val atEdit = listFiles(outDirectory)
            assertTrue(atEdit.isNotEmpty() && atEdit.all { it.endsWith(".part") }, "outputs should be open at the edit")
            atEdit()
            engine.migrate(edited, paused = false)
            runBlocking { engine.await() }.also { atEnd(engine) }
        }
        finally {
            gate.release()
            engine.close()
        }
    }


    /**
     * [text] is one [header] and then the numbered rows (of [group] of [groupedCsv], or every row when null) from
     * some row after the first to the last, each once.
     */
    private fun assertRowsAfterTheEdit(header: String, delimiter: String, text: String, group: Int? = null) {
        val lines = text.lines().dropLast(1)
        assertEquals(header, lines.first())
        val ids = lines.drop(1).map { line ->
            val cells = line.split(delimiter)
            val id = cells.first()
            assertEquals("v$id", cells.last())
            id.toInt()
        }
        val step = if (group == null) 1 else 2
        assertTrue(ids.first() > (group ?: 0), "the rows written before the edit should be gone")
        assertEquals((ids.first() until manyRows step step).toList(), ids)
        if (group != null) {
            assertEquals(group, ids.first() % 2)
        }
    }


    /** [reported] is Write's discards progress: one live edit, which discarded [outputs] open outputs. */
    private fun assertDiscarded(outputs: Long, reported: Any?) {
        val discards = assertIs<List<*>>(reported)
        val discard = assertIs<Map<*, *>>(discards.single())
        assertEquals(outputs, discard[JobOutputConventions.discardOutputsKey])
        assertTrue(discard[JobOutputConventions.discardAtKey] is Long)
    }


    private fun inflate(path: Path): String =
        GZIPInputStream(Files.newInputStream(path)).use { it.readAllBytes().decodeToString() }


    private fun writtenNames(outcome: Outcome): List<String> =
        collected(outcome).map { (it as Written).name }


    private fun worker(document: String, name: String): ObjectLocation =
        ObjectLocation(DocumentPath.parse(document), ObjectPath.parse("main.workers/$name"))


    private fun edited(document: String, worker: String, attribute: String, value: String): GraphNotation =
        edit(AutoTestUtils.readNotation(), worker(document, worker), attribute, value)


    /** One output per source file, each named by its group (the source's name). */
    private fun perSource(): GraphNotation {
        val grouped = edited(bytesDocument, "format", "groupBy", "parent.name")
        return edit(grouped, write, "name", "\$group")
    }


    private fun perSource(existing: String): GraphNotation =
        edit(perSource(), write, "existing", existing)


    private fun onChange(groupBy: String): GraphNotation {
        val grouped = edited(bytesDocument, "format", "groupBy", groupBy)
        return edit(grouped, worker(bytesDocument, "format"), "groupEnd", "change")
    }


    private fun validate(document: String, notation: GraphNotation = AutoTestUtils.readNotation()) =
        harness.fresh().let { context ->
            val graphDefinition = AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful
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


    /** Holds Write inside the first output, at its [gateAt]-th chunk, until released; deaf to interrupts. */
    private class WriteGate(
        private val gateAt: Int = gateAtWrite
    ) {
        private val writes = AtomicInteger()
        private val reached = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun intercept(encoded: OutputStream): OutputStream =
            object: FilterOutputStream(encoded) {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (writes.incrementAndGet() == gateAt) {
                        reached.countDown()
                        awaitRelease()
                    }
                    out.write(b, off, len)
                }
            }

        fun awaitReached() {
            check(reached.await(hangGuardMillis, TimeUnit.MILLISECONDS)) { "Write never reached chunk $gateAt" }
        }

        fun release() {
            released.countDown()
        }

        private fun awaitRelease() {
            var interrupted = false
            while (true) {
                try {
                    check(released.await(hangGuardMillis, TimeUnit.MILLISECONDS)) { "the gate was never released" }
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
