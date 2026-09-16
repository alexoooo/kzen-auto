package tech.kzen.auto.server.objects.job.worker.content

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.collected
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.listFiles
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.prepare
import tech.kzen.auto.server.objects.job.worker.content.ContentScopeHarness.Companion.prepareBig
import tech.kzen.auto.server.objects.job.worker.content.tar.TarGzEntryCursor
import tech.kzen.lib.common.exec.engine.Outcome
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * CS2 of the content streaming spike (docs/plans/2026-09-16_borrowed-elements.md): ReadPart inside the
 * scope with a declared schema, Take(n) outside and inside the scope, and downstream-completion propagation.
 */
class ScopeReadPartTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val barBytes = "a,b\n1,2\n3,4\n5,6\n7,8\n".toByteArray()

    private val cursors = CopyOnWriteArrayList<TarGzEntryCursor>()
    private val harness = ContentScopeHarness()


    @Before
    fun setUp() {
        cursors.clear()
        TarGzEntryCursor.observer = { cursors.add(it) }
        JobChannel.consumerCloseEnabled = true
    }


    @After
    fun tearDown() {
        TarGzEntryCursor.observer = null
        JobChannel.consumerCloseEnabled = true
        harness.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun readsCsvEntryWithDeclaredSchemaExactlyAsReadPartReadsTheExtractedFile() {
        val directory = prepare("read", listOf("foo.txt" to "hello".toByteArray(), "bar.csv" to barBytes))
        Files.write(directory.resolve("bar.csv"), barBytes)

        val scoped = collected(harness.run("test/job/content/scope-read-test.yaml"))
        val reference = collected(harness.run("test/job/content/read-file-test.yaml"))

        assertEquals(4, scoped.size)
        assertEquals(reference, scoped)
        assertEquals(reference.map { it.toString() }, scoped.map { it.toString() })
        assertEquals(listOf(1, 3, 5, 7), scoped.map { (it as Map<*, *>)["a"] })

        val cursor = cursors.single()
        assertEquals(1, cursor.closeCount())
        assertEquals(1, cursor.produced.size)
        assertTrue(cursor.produced.single().isInvalidated)
    }


    @Test
    fun rowsLeavingTheScopeAreRetainedBySortWithNoHolds() {
        prepare("sort", listOf("bar.csv" to barBytes))

        val outcome = harness.run("test/job/content/scope-read-sort-test.yaml")

        val rows = collected(outcome)
        assertEquals(listOf(7, 5, 3, 1), rows.map { (it as Map<*, *>)["a"] })
        assertEquals(1, cursors.single().closeCount())
    }


    @Test
    fun takeOutsideTheScopeCompletesAfterTenRowsWithoutDrainingTheEntry() {
        val rows = prepareBig()

        val started = System.nanoTime()
        val outcome = harness.run("test/job/content/scope-take-out-test.yaml")
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        val taken = collected(outcome)
        assertEquals((0 until 10).map { it.toString() }, taken.map { (it as Map<*, *>)["id"].toString() })

        val cursor = cursors.single()
        assertEquals(1, cursor.closeCount())
        assertTrue(cursor.isClosed)
        println("CS2 take-out: $rows-row entry, 10 taken, ${elapsedMillis} ms")
        assertTrue(elapsedMillis < 60_000, "Take(10) over $rows rows took $elapsedMillis ms")
    }


    @Test
    fun withoutConsumerCloseTheEngineReportsTheCompletedTakeAsADeadlock() {
        prepareBig()
        JobChannel.consumerCloseEnabled = false

        val outcome = harness.run("test/job/content/scope-take-out-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "deadlock")
        assertEquals(1, cursors.single().closeCount())
    }


    @Test
    fun takeInsideTheBodyWritesTenEntriesAndNeverOpensTheEleventh() {
        val directory = prepare("take-in", twentyEntries())
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/scope-take-in-test.yaml")

        val written = collected(outcome).map { (it as Written).name }
        assertEquals((0 until 10).map { "e%02d.txt".format(it) }, written)
        assertEquals((0 until 10).map { "e%02d.txt.gz".format(it) }.toSet(), listFiles(out))

        val cursor = cursors.single()
        assertEquals(10, cursor.produced.size)
        assertEquals(1, cursor.closeCount())
    }


    @Test
    fun takeAfterTheWriterPublishesTenAndLeavesNoTempFile() {
        val directory = prepare("write-take", twentyEntries())
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/scope-write-take-test.yaml")

        val taken = collected(outcome).map { (it as Written).name }
        assertEquals((0 until 10).map { "e%02d.txt".format(it) }, taken)

        val files = listFiles(out)
        assertTrue(files.none { it.contains(".part") }, "temp files left: $files")
        // The scope learns of the closed downstream at the flush that follows an entry, so the entry it was
        // on when Take completed may already be published: ten or eleven files, never more
        assertTrue(files.size in 10..11, "published: $files")

        val cursor = cursors.single()
        assertTrue(cursor.produced.size in 10..11, "opened: ${cursor.produced.size}")
        assertEquals(1, cursor.closeCount())
        println("CS2 write-take: ${files.size} published, ${cursor.produced.size} entries opened")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun twentyEntries(): List<Pair<String, ByteArray?>> =
        (0 until 20).map { "e%02d.txt".format(it) to "entry $it".toByteArray() }
}
