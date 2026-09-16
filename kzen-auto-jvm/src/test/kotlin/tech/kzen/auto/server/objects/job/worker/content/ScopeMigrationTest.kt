package tech.kzen.auto.server.objects.job.worker.content

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.exec.job.EngineJobControl
import tech.kzen.auto.server.exec.job.JobLogic
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.collected
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.edit
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.jobLocation
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.listFiles
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.prepare
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.prepareCsvArchive
import tech.kzen.auto.server.objects.job.worker.content.scope.ContentWriter
import tech.kzen.auto.server.objects.job.worker.content.tar.TarGzEntryCursor
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.server.exec.engine.RunEngine
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * CS3 of the content streaming spike (docs/plans/2026-09-16_borrowed-elements.md §4): a live edit lands
 * at an entry boundary, consumers downstream of the scope keep draining until the scope reaches it, the open
 * cursor is adopted (never re-opened), and an edit of the source selection is refused by name before anything
 * is detached.
 */
class ScopeMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val writeDocument = "test/job/content/scope-migrate-write-test.yaml"
        private const val summaryDocument = "test/job/content/scope-migrate-summary-test.yaml"
        private const val entryCount = 10
        private const val editInsideEntry = 3
        // Enough rows that the pause is requested well inside the entry, few enough that the Summary is quick
        private const val summaryRows = 100_000
        private const val waitSeconds = 30L
        private const val pathRefusal = "Source selection of Archive changed. Start a new run to apply it."
    }


    private val writeJob = jobLocation(writeDocument)
    private val writeScope = ObjectLocation(
        DocumentPath.parse(writeDocument), ObjectPath.parse("main.workers/Archive"))
    private val writeBody = ObjectLocation(
        DocumentPath.parse(writeDocument), ObjectPath.parse("main.workers/Archive.body/Write"))
    private val summaryJob = jobLocation(summaryDocument)
    private val summaryWorker = ObjectLocation(
        DocumentPath.parse(summaryDocument), ObjectPath.parse("main.workers/summary"))
    private val summarySink = ObjectLocation(
        DocumentPath.parse(summaryDocument), ObjectPath.parse("main.workers/collect"))

    private val cursors = CopyOnWriteArrayList<TarGzEntryCursor>()
    private val harness = ContentScopeHarness()


    @Before
    fun setUp() {
        cursors.clear()
        TarGzEntryCursor.observer = { cursors.add(it) }
        EngineJobControl.drainingEnabled = true
    }


    @After
    fun tearDown() {
        TarGzEntryCursor.observer = null
        ContentWriter.encoderInterceptor = null
        EngineJobControl.drainingEnabled = true
        harness.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun codingEditAppliesFromTheEntryAfterTheOneItLandedIn() {
        val out = prepare("migrate-write", entries()).resolve("out")
        val notation = AutoTestUtils.readNotation()
        harness.fresh()
        val base = harness.compile(writeJob, notation)
        val edited = harness.compile(writeJob, edit(notation, writeBody, "coding", ContentWriter.codingNone))

        val outcome = editInsideEntry(base, edited)

        val names = collected(outcome).map { (it as Written).name }
        assertEquals(entryNames(0 until entryCount), names)
        // The entry the edit landed in finished under its original coding; everything after is plain
        val expectedFiles = entryNames(0 until editInsideEntry).map { "$it.gz" } +
                entryNames(editInsideEntry until entryCount)
        assertEquals(expectedFiles.toSet(), listFiles(out))
        assertSingleCursorAdopted(entryCount)
    }


    @Test
    fun entriesEditAppliesFromTheNextHeaderWithoutRewinding() {
        prepare("migrate-write", entries())
        val notation = AutoTestUtils.readNotation()
        harness.fresh()
        val base = harness.compile(writeJob, notation)
        val selected = listOf("e07.txt", "e08.txt", "e09.txt")
        val edited = harness.compile(
            writeJob,
            edit(notation, writeScope, "entries",
                ListAttributeNotation(selected.map { ScalarAttributeNotation(it) }.toPersistentList())))

        val outcome = editInsideEntry(base, edited)

        val names = collected(outcome).map { (it as Written).name }
        assertEquals(entryNames(0 until editInsideEntry) + selected, names)
        // The cursor never rewinds: the entries between the edit and the new selection are skipped at the
        // header, never opened
        assertSingleCursorAdopted(editInsideEntry + selected.size)
    }


    @Test
    fun pathEditIsRefusedByNameBeforeAnythingIsDetachedAndTheRunContinues() {
        prepare("migrate-write", entries())
        val notation = AutoTestUtils.readNotation()
        harness.fresh()
        val base = harness.compile(writeJob, notation)
        val edited = harness.compile(
            writeJob, edit(notation, writeScope, "path", "build/content-scope/migrate-write/other.tar.gz"))
        val compatible = harness.compile(writeJob, edit(notation, writeBody, "coding", ContentWriter.codingNone))

        // Judged from notation alone: no run needed, nothing touched
        assertEquals(pathRefusal, base.refuseMigration(edited))
        assertNull(base.refuseMigration(compatible))

        val gate = EntryGate(editInsideEntry)
        ContentWriter.encoderInterceptor = gate::intercept
        val engine = harness.engine(base, writeJob)
        try {
            engine.resume()
            gate.awaitReached()
            engine.pause()
            gate.release()
            engine.awaitQuiescent()
            // The refused edit is not applied: the run continues under its own definition, cursor untouched
            assertEquals(pathRefusal, base.refuseMigration(edited))
            assertEquals(0, cursors.single().closeCount())
            engine.resume()
            val outcome = runBlocking { engine.await() }
            assertEquals(entryNames(0 until entryCount), collected(outcome).map { (it as Written).name })
        }
        finally {
            engine.close()
        }
        assertSingleCursorAdopted(entryCount)
    }


    @Test
    fun consumerBelowTheScopeKeepsDrainingSoThePauseLandsAtTheEntryBoundary() {
        val rows = prepareCsvArchive("migrate-summary", summaryRows)
        val notation = AutoTestUtils.readNotation()
        harness.fresh()
        val base = harness.compile(summaryJob, notation)
        val edited = harness.compile(summaryJob, edit(notation, summarySink, "result", "main"))
        val engine = harness.engine(base, summaryJob)
        try {
            engine.resume()
            val countBefore = awaitSummaryCounting(engine)
            assertTrue(countBefore < rows, "the pause must be requested inside the entry (count $countBefore)")
            engine.pause()
            engine.awaitQuiescent()
            // Upstream-first: the Summary drained the rest of the entry, so the scope quiesced at its boundary
            val cursor = cursors.single()
            assertTrue(cursor.produced.single().isInvalidated, "the entry should have ended before the barrier")

            engine.migrate(edited, paused = false)
            val outcome = runBlocking { engine.await() }
            assertIs<Outcome.Success>(outcome)
            assertEquals(
                rows.toLong(), harness.workerProgress(engine, summaryWorker, "count"),
                "every row counted exactly once: a re-read would exceed $rows, a lost carry would fall short")
        }
        finally {
            engine.close()
        }
        assertSingleCursorAdopted(1)
    }


    @Test
    fun withoutDrainingTheRunQuiescesInsideTheEntryAndTheRebuiltScopeFailsByName() {
        val rows = prepareCsvArchive("migrate-summary", summaryRows)
        EngineJobControl.drainingEnabled = false
        val notation = AutoTestUtils.readNotation()
        harness.fresh()
        val base = harness.compile(summaryJob, notation)
        val edited = harness.compile(summaryJob, edit(notation, summarySink, "result", "main"))
        val engine = harness.engine(base, summaryJob)
        try {
            engine.resume()
            val countBefore = awaitSummaryCounting(engine)
            assertTrue(countBefore < rows)
            engine.pause()
            engine.awaitQuiescent()
            // The Summary parked at its checkpoint, the bounded channel filled, and the scope parked inside the
            // entry on its send: the pre-spike wavefront
            assertFalse(cursors.single().produced.single().isInvalidated, "the scope should be inside the entry")

            engine.migrate(edited, paused = false)
            val outcome = runBlocking { engine.await() }
            val failed = assertIs<Outcome.Failed>(outcome)
            assertContains(failed.message, "inside entry 'big.csv'")
        }
        finally {
            engine.close()
        }
        assertEquals(1, cursors.single().closeCount())
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** Runs the write Job, pauses while the writer is inside entry [editInsideEntry], applies [edited], completes. */
    private fun editInsideEntry(base: JobLogic, edited: JobLogic): Outcome {
        val gate = EntryGate(editInsideEntry)
        ContentWriter.encoderInterceptor = gate::intercept
        val engine = harness.engine(base, writeJob)
        try {
            engine.resume()
            gate.awaitReached()
            engine.pause()
            gate.release()
            engine.awaitQuiescent()
            assertEquals(editInsideEntry, cursors.single().produced.size)
            engine.migrate(edited, paused = false)
            return runBlocking { engine.await() }
        }
        finally {
            engine.close()
        }
    }


    private fun awaitSummaryCounting(engine: RunEngine): Long {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSeconds)
        while (true) {
            val count = harness.workerProgress(engine, summaryWorker, "count") as? Long ?: 0L
            if (count > 0L) {
                return count
            }
            assertTrue(System.nanoTime() < deadlineNanos, "the Summary never started counting")
            Thread.sleep(1)
        }
    }


    /** One cursor for the whole run (the archive was never re-opened), closed once, [produced] entries opened. */
    private fun assertSingleCursorAdopted(produced: Int) {
        val cursor = cursors.single()
        assertEquals(produced, cursor.produced.size)
        assertEquals(1, cursor.closeCount())
    }


    private fun entries(): List<Pair<String, ByteArray?>> =
        (0 until entryCount).map { "e%02d.txt".format(it) to "entry $it".toByteArray() }


    private fun entryNames(range: IntRange): List<String> =
        range.map { "e%02d.txt".format(it) }


    /** Holds the writer inside the [atEntry]-th entry (counted from 1) until released, via the encoder seam. */
    private class EntryGate(private val atEntry: Int) {
        private val seen = AtomicInteger()
        private val reached = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun intercept(stream: OutputStream): OutputStream {
            if (seen.incrementAndGet() == atEntry) {
                reached.countDown()
                check(released.await(waitSeconds, TimeUnit.SECONDS)) { "gate never released" }
            }
            return stream
        }

        fun awaitReached() {
            check(reached.await(waitSeconds, TimeUnit.SECONDS)) { "writer never reached entry $atEntry" }
        }

        fun release() {
            released.countDown()
        }
    }
}
