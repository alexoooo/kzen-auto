package tech.kzen.auto.server.objects.job.worker.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.edit
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.jobLocation
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.prepareCsvFile
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.server.exec.engine.RunEngine
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Live edit of a `File` source (docs/plans/2026-09-16_borrowed-elements.md §3.6, BE3): the pre-detach refusal
 * generalized from the `Entries` archive path to every source's selection ([WorkerBase.migrationKey]) — an edit
 * that changes the file selection is refused by name from the live instance, nothing is detached, and the run
 * completes under its own definition; an edit elsewhere is compatible.
 */
class FileSourceMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val document = "test/job/content/file-migrate-test.yaml"
        private const val rows = 100_000
        private const val waitSeconds = 30L
        private const val selectionRefusal = "Source selection of read changed. Start a new run to apply it."
    }


    private val job = jobLocation(document)
    private val source = ObjectLocation(DocumentPath.parse(document), ObjectPath.parse("main.workers/read"))
    private val summary = ObjectLocation(DocumentPath.parse(document), ObjectPath.parse("main.workers/summary"))
    private val sink = ObjectLocation(DocumentPath.parse(document), ObjectPath.parse("main.workers/collect"))

    private val harness = ContentTestHarness()


    @After
    fun tearDown() {
        harness.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun fileSelectionEditIsRefusedByNameFromTheLiveRunAndTheRunContinues() {
        prepareCsvFile("file-migrate", rows)
        val notation = AutoTestUtils.readNotation()
        harness.fresh()
        val base = harness.compile(job, notation)
        val otherFile = MapAttributeNotation(persistentMapOf(
            AttributeSegment.ofKey("location") to
                    ScalarAttributeNotation("build/content-scope/file-migrate/other.csv")))
        val edited = harness.compile(
            job, edit(notation, source, "files", ListAttributeNotation(listOf(otherFile).toPersistentList())))
        val compatible = harness.compile(job, edit(notation, sink, "result", "main"))

        // Judged by the live instances: with no run there is nothing to refuse
        assertNull(base.refuseMigration(edited))

        val engine = harness.engine(base, job)
        try {
            engine.resume()
            val countBefore = awaitSummaryCounting(engine)
            assertTrue(countBefore < rows, "the pause must be requested inside the file (count $countBefore)")
            engine.pause()
            engine.awaitQuiescent()

            assertEquals(selectionRefusal, base.refuseMigration(edited))
            assertNull(base.refuseMigration(compatible))

            // The refused edit is not applied: the run continues under its own definition, its reader untouched
            engine.resume()
            val outcome = runBlocking { engine.await() }
            assertIs<Outcome.Success>(outcome)
            assertEquals(
                rows.toLong(), harness.workerProgress(engine, summary, "count"),
                "every row counted exactly once: a restarted read would exceed $rows")
        }
        finally {
            engine.close()
        }
        assertNull(base.refuseMigration(edited))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun awaitSummaryCounting(engine: RunEngine): Long {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSeconds)
        while (true) {
            val count = harness.workerProgress(engine, summary, "count") as? Long ?: 0L
            if (count > 0L) {
                return count
            }
            assertTrue(System.nanoTime() < deadlineNanos, "the Summary never started counting")
            Thread.sleep(1)
        }
    }
}
