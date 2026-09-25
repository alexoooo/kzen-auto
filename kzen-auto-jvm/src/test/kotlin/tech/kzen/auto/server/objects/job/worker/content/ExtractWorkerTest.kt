package tech.kzen.auto.server.objects.job.worker.content

import com.linkedin.migz.MiGzOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.collected
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.listFiles
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.prepare
import tech.kzen.auto.server.objects.job.worker.content.tar.TarGzEntryCursor
import tech.kzen.lib.common.exec.engine.Outcome
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.io.path.name
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * The borrowed-element protocol over `File(units) → Extract` (docs/plans/2026-09-16_borrowed-elements.md BE1; the
 * spike's CS1 rows, flat): a generated tar.gz filtered and written through a real run, one entry lent at a
 * time.
 */
class ExtractWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val fooBytes = "hello".toByteArray()
    private val barBytes = "a,b\n1,2\n3,4\n5,6\n7,8\n".toByteArray()
    private val bazBytes = "0123456789".toByteArray()
    private val nestedBytes = "nested-bytes".toByteArray()

    private val standardEntries = listOf(
        "foo.txt" to fooBytes,
        "bar.csv" to barBytes,
        "sub/" to null,
        "sub/data.bin" to nestedBytes,
        "baz.txt" to bazBytes)

    private val cursors = CopyOnWriteArrayList<TarGzEntryCursor>()
    private val harness = ContentTestHarness()


    @Before
    fun setUp() {
        cursors.clear()
        TarGzEntryCursor.observer = { cursors.add(it) }
        WriteWorker.encoderInterceptor = null
        StashingWorker.reset()
        HoldingWorker.reset()
        BlockingWorker.reset()
        LabelsWorker.reset()
    }


    @After
    fun tearDown() {
        TarGzEntryCursor.observer = null
        WriteWorker.encoderInterceptor = null
        HoldingWorker.reset()
        harness.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun writesSelectedTxtEntriesAsGzipByteIdenticalToTheExportEncoder() {
        val directory = prepare("write", standardEntries)
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-write-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("foo.txt.gz", "baz.txt.gz"), listFiles(out))
        assertEquals(fooBytes.toList(), inflate(out.resolve("foo.txt.gz")).toList())
        assertEquals(bazBytes.toList(), inflate(out.resolve("baz.txt.gz")).toList())
        assertEquals(migz(fooBytes).toList(), Files.readAllBytes(out.resolve("foo.txt.gz")).toList())
        assertEquals(migz(bazBytes).toList(), Files.readAllBytes(out.resolve("baz.txt.gz")).toList())

        assertEquals(listOf("foo.txt", "baz.txt"), writtenNames(outcome))

        val cursor = cursors.single()
        assertEquals(1, cursor.closeCount())
        // Every file member is lent (the sub/ directory is not); the Filter drops bar.csv and sub/data.bin
        // without opening them
        assertEquals(4, cursor.produced.size)
        assertEquals(2, cursor.produced.count { it.wasOpened })
        assertTrue(cursor.produced.all { it.isInvalidated })
    }


    @Test
    fun plainCompressionCopiesBytesWithoutSuffix() {
        val directory = prepare("plain", standardEntries)
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-plain-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("foo.txt", "baz.txt"), listFiles(out))
        assertEquals(fooBytes.toList(), Files.readAllBytes(out.resolve("foo.txt")).toList())
        assertEquals(bazBytes.toList(), Files.readAllBytes(out.resolve("baz.txt")).toList())
    }


    @Test
    fun nameInterpolatesTheTimeTheWriteStarted() {
        val out = prepare("time", standardEntries).resolve("out")

        val outcome = harness.run("test/job/content/extract-time-test.yaml")

        assertIs<Outcome.Success>(outcome)
        val written = listFiles(out)
        assertEquals(setOf("foo.txt", "baz.txt"), written.map { it.substringAfter('/') }.toSet())
        // One run, one time: Report's export format, local yyyyMMddTHHmmss
        val times = written.map { it.substringBefore('/') }.toSet()
        assertEquals(1, times.size, "$written")
        assertTrue(Regex("\\d{8}T\\d{6}").matches(times.single()), "$times")
    }


    @Test
    fun filterOnSizeSelectsTheSameWayAsHeaderSelectionAndNeverOpensDroppedEntries() {
        val directory = prepare("filter", standardEntries)
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-filter-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("bar.csv.gz", "sub/data.bin.gz", "baz.txt.gz"), listFiles(out))
        assertEquals(nestedBytes.toList(), inflate(out.resolve("sub/data.bin.gz")).toList())

        val cursor = cursors.single()
        assertEquals(4, cursor.produced.size)
        assertEquals(3, cursor.produced.count { it.wasOpened })
        assertEquals(1, cursor.closeCount())
    }


    @Test
    fun nameInterpolatesParentName() {
        val directory = prepare("nested", standardEntries)
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-nested-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            setOf("input.tar.gz/foo.txt.gz", "input.tar.gz/bar.csv.gz",
                "input.tar.gz/sub/data.bin.gz", "input.tar.gz/baz.txt.gz"),
            listFiles(out))
    }


    @Test
    fun twoSelectedArchivesFlowInSelectionOrderAndSplitByParentName() {
        val directory = prepare("two", standardEntries)
        ContentTestHarness.writeArchive(
            directory.resolve("second.tar.gz"), listOf("alpha.txt" to fooBytes, "beta.csv" to barBytes))
        val out = directory.resolve("out")
        val firstClosedBeforeSecondOpened = ArrayList<Boolean>()
        TarGzEntryCursor.observer = { opened ->
            firstClosedBeforeSecondOpened.add(cursors.all { it.closeCount() == 1 })
            cursors.add(opened)
        }

        val outcome = harness.run("test/job/content/extract-two-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            setOf("input.tar.gz/foo.txt.gz", "input.tar.gz/baz.txt.gz", "second.tar.gz/alpha.txt.gz"),
            listFiles(out))
        assertEquals(listOf("foo.txt", "baz.txt", "alpha.txt"), writtenNames(outcome))
        assertEquals(listOf("input.tar.gz", "second.tar.gz"), cursors.map { it.archiveFileName })
        assertEquals(listOf(true, true), firstClosedBeforeSecondOpened)
        assertTrue(cursors.all { it.closeCount() == 1 })
    }


    @Test
    fun nestedArchiveIsExtractedByASecondExtract() {
        val directory = prepare("inner", emptyList())
        val innerArchive = directory.resolve("inner.tar.gz")
        ContentTestHarness.writeArchive(innerArchive, listOf("foo.txt" to fooBytes, "bar.csv" to barBytes))
        ContentTestHarness.writeArchive(
            directory.resolve("input.tar.gz"),
            listOf("inner.tar.gz" to Files.readAllBytes(innerArchive), "note.txt" to bazBytes))
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-inner-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("inner.tar.gz/foo.txt.gz", "inner.tar.gz/bar.csv.gz"), listFiles(out))
        assertEquals(fooBytes.toList(), inflate(out.resolve("inner.tar.gz/foo.txt.gz")).toList())
        assertEquals(listOf("input.tar.gz", "inner.tar.gz"), cursors.map { it.archiveFileName })
        assertTrue(cursors.all { it.closeCount() == 1 })
    }


    @Test
    fun fileLeftOnAutomaticHandsExtractTheArchiveWhole() {
        val directory = prepare("items", standardEntries)

        val outcome = harness.run("test/job/content/extract-items-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(listOf("input.tar.gz"), cursors.map { it.archiveFileName })
        assertContains(listFiles(directory.resolve("out")), "foo.txt.gz")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun contentReadAfterEntryEndFailsByName() {
        prepare("stash", standardEntries)

        val outcome = harness.run("test/job/content/extract-stash-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "foo.txt")
        assertContains(failed.message, "released")
        val failure = assertNotNull(StashingWorker.readFailure)
        assertContains(failure.message ?: "", "foo.txt")
        assertEquals(1, cursors.single().closeCount())
    }


    @Test
    fun retentionOfAnEntryIsRefusedByNameBeforeTheCursorAdvances() {
        prepare("hold", standardEntries)

        val outcome = harness.run("test/job/content/extract-hold-test.yaml")

        // Refused at the first entry (§3.3), naming the Worker, the entry and its lender — not a deadlock later
        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Hold")
        assertContains(failed.message, "cannot keep entry 'foo.txt'")
        assertContains(failed.message, "borrowed from 'input.tar.gz'")
        assertEquals(1, HoldingWorker.seen)
        assertTrue(HoldingWorker.leases.isEmpty(), "no lease was handed out")
        // The refusal releases the entry, so the source may advance one pull (never opened) before the run's
        // cancellation reaches it — as the take-in test allows
        val cursor = cursors.single()
        assertTrue(cursor.produced.size in 1..2, "pulled: ${cursor.produced.size}")
        assertEquals(0, cursor.produced.count { it.wasOpened })
        assertEquals(1, cursor.closeCount())
    }


    @Test
    fun downstreamFailureClosesTheArchiveOnce() {
        prepare("fail", standardEntries)

        val outcome = harness.run("test/job/content/extract-fail-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "injected failure")
        assertEquals(1, cursors.single().closeCount())
    }


    @Test
    fun cancellationInsideAnEntryClosesTheArchiveOnce() {
        val big = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        prepare("block", listOf("big.bin" to big, "after.txt" to fooBytes))

        val engine = harness.start("test/job/content/extract-block-test.yaml")
        val outcome = try {
            runBlocking {
                val terminal = async { engine.await() }
                engine.resume()
                assertTrue(BlockingWorker.entered.await(30, TimeUnit.SECONDS), "worker never entered the entry")
                engine.cancel()
                terminal.await()
            }
        }
        finally {
            engine.close()
        }

        assertIs<Outcome.Cancelled>(outcome)
        val cursor = cursors.single()
        assertEquals(1, cursor.produced.size)
        assertEquals(1, cursor.closeCount())
        assertTrue(cursor.isClosed)
    }


    @Test
    fun valueDerivedFromAnEntryFlowsThroughTwoWorkersAndReleasesTheEntry() {
        // The spike's §10 deadlock case: a Formula output inherits the entry's owners, so the entry is held by
        // the Formula's callback, then the channel, then the next Worker's callback, and released when that returns
        prepare("formula", standardEntries)

        val outcome = harness.run("test/job/content/extract-formula-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(listOf("foo.txt!", "bar.csv!", "sub/data.bin!", "baz.txt!"), LabelsWorker.labels.toList())
        val cursor = cursors.single()
        assertEquals(4, cursor.produced.size)
        assertTrue(cursor.produced.all { it.isInvalidated })
        assertEquals(1, cursor.closeCount())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun escapingEntryNameFailsByNameAndWritesNothing() {
        val directory = prepare("escape", listOf("ok.txt" to fooBytes, "../escape.txt" to fooBytes))
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-escape-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "../escape.txt")
        assertEquals(setOf("ok.txt.gz"), listFiles(out))
        assertFalse(Files.exists(directory.resolve("escape.txt.gz")))
        assertFalse(Files.exists(directory.resolve("escape.txt")))
    }


    @Test
    fun duplicateNameFailsUnderExistingFail() {
        val directory = prepare("duplicate", listOf("dup.txt" to fooBytes, "dup.txt" to bazBytes))
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-duplicate-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "dup.txt")
        assertContains(failed.message, "already exists")
        assertEquals(setOf("dup.txt.gz"), listFiles(out))
        assertEquals(fooBytes.toList(), inflate(out.resolve("dup.txt.gz")).toList())
    }


    @Test
    fun duplicateNameIsSkippedUnderExistingSkip() {
        val directory = prepare("skip", listOf("dup.txt" to fooBytes, "dup.txt" to bazBytes))
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-skip-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(listOf("dup.txt"), writtenNames(outcome))
        assertEquals(fooBytes.toList(), inflate(out.resolve("dup.txt.gz")).toList())
    }


    @Test
    fun duplicateNameIsReplacedUnderExistingReplace() {
        val directory = prepare("replace", listOf("dup.txt" to fooBytes, "dup.txt" to bazBytes))
        val out = directory.resolve("out")

        val outcome = harness.run("test/job/content/extract-replace-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(listOf("dup.txt", "dup.txt"), writtenNames(outcome))
        assertEquals(setOf("dup.txt.gz"), listFiles(out))
        assertEquals(bazBytes.toList(), inflate(out.resolve("dup.txt.gz")).toList())
    }


    @Test
    fun encoderFinalizationFailureLeavesNoFileAndNoTemporary() {
        val directory = prepare("write", standardEntries)
        val out = directory.resolve("out")
        WriteWorker.encoderInterceptor = { encoded ->
            object: FilterOutputStream(encoded) {
                override fun close() {
                    throw IOException("injected finalization failure")
                }
            }
        }

        val outcome = harness.run("test/job/content/extract-write-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "injected finalization failure")
        assertEquals(emptySet(), listFiles(out))
        assertEquals(1, cursors.single().closeCount())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun liveHeapStaysFlatAcrossAHundredMebibyteEntry() {
        // KZEN_CONTENT_SPIKE_MIB overrides the entry size (the plan's 1 GiB case: 1024), off the default path for time
        val size = (System.getenv("KZEN_CONTENT_SPIKE_MIB")?.toLongOrNull() ?: 100L) * 1024 * 1024
        val directory = prepare("large", emptyList())
        ContentTestHarness.writeLargeArchive(directory.resolve("input.tar.gz"), "large.bin", size) {
            (it * 7 % 253).toByte()
        }
        val out = directory.resolve("out")

        val baseline = liveHeap()
        val peakHolder = java.util.concurrent.atomic.AtomicLong()
        val sampler = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    peakHolder.accumulateAndGet(liveHeap(), ::maxOf)
                    Thread.sleep(100)
                }
            }
            catch (_: InterruptedException) {}
        }
        sampler.isDaemon = true
        sampler.start()
        val outcome = try {
            harness.run("test/job/content/extract-large-test.yaml")
        }
        finally {
            sampler.interrupt()
            sampler.join()
        }

        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("large.bin.gz"), listFiles(out))
        assertEquals(size, inflatedLength(out.resolve("large.bin.gz")))
        val growth = peakHolder.get() - baseline
        println("content spike: entry ${size / (1024 * 1024)} MiB, live heap growth ${growth / (1024 * 1024)} MiB")
        assertTrue(growth < 64L * 1024 * 1024, "live heap grew by ${growth / (1024 * 1024)} MiB")
    }


    @Test
    fun manySmallEntriesMeasurePerEntryCost() {
        // KZEN_CONTENT_SPIKE_ENTRIES overrides the entry count (the plan's case: 10000), off the default path for
        // time: each entry is a published file, which costs milliseconds on Windows
        val count = System.getenv("KZEN_CONTENT_SPIKE_ENTRIES")?.toIntOrNull() ?: 500
        val directory = prepare("many", (0 until count).map { "e$it.txt" to "entry $it".toByteArray() })
        val out = directory.resolve("out")

        val startNanos = System.nanoTime()
        val outcome = harness.run("test/job/content/extract-many-test.yaml")
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertIs<Outcome.Success>(outcome)
        assertEquals(count, listFiles(out).size)
        assertEquals(count, writtenNames(outcome).size)
        println(
            "content spike: $count entries in $elapsedMillis ms " +
                    "(${elapsedMillis * 1000 / count} us/entry, run setup included)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun inflate(path: Path): ByteArray =
        GZIPInputStream(Files.newInputStream(path)).use { it.readAllBytes() }


    private fun inflatedLength(path: Path): Long {
        GZIPInputStream(Files.newInputStream(path), 64 * 1024).use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return total
                total += count
            }
        }
    }


    /** The same encoder and parameters as `ExportCompression.GZip`. */
    private fun migz(bytes: ByteArray): ByteArray {
        val raw = ByteArrayOutputStream()
        MiGzOutputStream(raw as OutputStream, 7, MiGzOutputStream.DEFAULT_BLOCK_SIZE).use { it.write(bytes) }
        return raw.toByteArray()
    }


    private fun liveHeap(): Long {
        System.gc()
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }


    private fun writtenNames(outcome: Outcome): List<String> =
        collected(outcome).map { (it as Written).name }
}
