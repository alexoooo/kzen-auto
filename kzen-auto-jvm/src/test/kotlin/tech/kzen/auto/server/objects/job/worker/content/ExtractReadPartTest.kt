package tech.kzen.auto.server.objects.job.worker.content

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.collected
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.listFiles
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.prepare
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.prepareBig
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.prepareBigFile
import tech.kzen.auto.server.objects.job.worker.content.tar.TarGzEntryCursor
import tech.kzen.lib.common.exec.engine.Outcome
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * `Read part` downstream of an `Extract` transform (docs/plans/2026-09-16_borrowed-elements.md BE2; the spike's CS2
 * rows, flat): rows read with a declared schema are independent of the entry, Take(n) below the rows and below
 * the entries, and downstream-completion propagation through the source.
 */
class ExtractReadPartTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val barBytes = "a,b\n1,2\n3,4\n5,6\n7,8\n".toByteArray()

    private val cursors = CopyOnWriteArrayList<TarGzEntryCursor>()
    private val harness = ContentTestHarness()


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
    fun readsCsvEntryWithDeclaredSchemaExactlyAsFileReadsTheExtractedFile() {
        val directory = prepare("read", listOf("foo.txt" to "hello".toByteArray(), "bar.csv" to barBytes))
        Files.write(directory.resolve("bar.csv"), barBytes)

        val read = collected(harness.run("test/job/content/extract-read-test.yaml"))
        val reference = collected(harness.run("test/job/content/read-file-test.yaml"))

        assertEquals(4, read.size)
        assertEquals(reference, read)
        assertEquals(reference.map { it.toString() }, read.map { it.toString() })
        assertEquals(listOf(1, 3, 5, 7), read.map { (it as Map<*, *>)["a"] })

        val cursor = cursors.single()
        assertEquals(1, cursor.closeCount())
        assertEquals(2, cursor.produced.size)
        assertEquals(1, cursor.produced.count { it.wasOpened })
        assertTrue(cursor.produced.all { it.isInvalidated })
    }


    @Test
    fun rowsReadFromAnEntryAreIndependentSoASortMayRetainThem() {
        prepare("sort", listOf("bar.csv" to barBytes))

        val outcome = harness.run("test/job/content/extract-read-sort-test.yaml")

        val rows = collected(outcome)
        assertEquals(listOf(7, 5, 3, 1), rows.map { (it as Map<*, *>)["a"] })
        assertEquals(1, cursors.single().closeCount())
    }


    @Test
    fun takeBelowTheRowsCompletesAfterTenRowsWithoutDrainingTheEntry() {
        val rows = prepareBig()

        val started = System.nanoTime()
        val outcome = harness.run("test/job/content/extract-take-out-test.yaml")
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        val taken = collected(outcome)
        assertEquals((0 until 10).map { it.toString() }, taken.map { (it as Map<*, *>)["id"].toString() })

        val cursor = cursors.single()
        assertEquals(1, cursor.closeCount())
        assertTrue(cursor.isClosed)
        println("BE2 take-out: $rows-row entry, 10 taken, ${elapsedMillis} ms")
        assertTrue(elapsedMillis < 60_000, "Take(10) over $rows rows took $elapsedMillis ms")
    }


    @Test
    fun withoutConsumerCloseTheEngineReportsTheCompletedTakeAsADeadlock() {
        prepareBig()
        JobChannel.consumerCloseEnabled = false

        val outcome = harness.run("test/job/content/extract-take-out-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "deadlock")
        assertEquals(1, cursors.single().closeCount())
    }


    @Test
    fun takeBelowTheEntriesWritesTenAndNeverOpensTheEleventh() {
        val directory = prepare("take-in", twentyEntries())
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-take-in-test.yaml")

        val written = collected(outcome).map { (it as Written).name }
        assertEquals((0 until 10).map { "e%02d.txt".format(it) }, written)
        assertEquals((0 until 10).map { "e%02d.txt.gz".format(it) }.toSet(), listFiles(out))

        // The source learns of the closed downstream at its next send, so it may have pulled (never opened) an
        // eleventh entry
        val cursor = cursors.single()
        assertTrue(cursor.produced.size in 10..11, "pulled: ${cursor.produced.size}")
        assertEquals(10, cursor.produced.count { it.wasOpened })
        assertEquals(1, cursor.closeCount())
        println("BE2 take-in: ${cursor.produced.size} entries pulled, 10 opened")
    }


    @Test
    fun takeAfterTheWriterPublishesTenAndLeavesNoTempFile() {
        val directory = prepare("write-take", twentyEntries())
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-write-take-test.yaml")

        val taken = collected(outcome).map { (it as Written).name }
        assertEquals((0 until 10).map { "e%02d.txt".format(it) }, taken)

        val files = listFiles(out)
        assertTrue(files.none { it.contains(".part") }, "temp files left: $files")
        // Write learns of the closed downstream at the flush that follows an entry, so the entry it was on when
        // Take completed may already be published: ten or eleven files, never more; the source may then have
        // pulled one more entry before its own send failed
        assertTrue(files.size in 10..11, "published: $files")

        val cursor = cursors.single()
        assertTrue(cursor.produced.size in 10..12, "pulled: ${cursor.produced.size}")
        assertEquals(files.size, cursor.produced.count { it.wasOpened })
        assertEquals(1, cursor.closeCount())
        println("BE2 write-take: ${files.size} published, ${cursor.produced.size} entries pulled")
    }


    @Test
    fun plainFileSourceUpstreamOfATakeCompletesCleanly() {
        val rows = prepareBigFile("file-take")

        val started = System.nanoTime()
        val outcome = harness.run("test/job/content/file-take-test.yaml")
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        val taken = collected(outcome)
        assertEquals((0 until 10).map { it.toString() }, taken.map { (it as Map<*, *>)["id"].toString() })
        println("BE2 file-take: $rows-row file, 10 taken, ${elapsedMillis} ms")
        assertTrue(elapsedMillis < 60_000, "File -> Take(10) over $rows rows took $elapsedMillis ms")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun twentyEntries(): List<Pair<String, ByteArray?>> =
        (0 until 20).map { "e%02d.txt".format(it) to "entry $it".toByteArray() }
}
