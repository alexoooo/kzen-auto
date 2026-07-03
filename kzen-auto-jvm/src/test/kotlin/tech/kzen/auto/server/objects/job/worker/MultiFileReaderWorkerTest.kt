package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * Unit test for [MultiFileReaderWorker]: driven through its real [MultiFileReaderWorker.run] lifecycle over temp
 * CSV files, it asserts the three things that make it a faithful multi-file reader —
 *
 * 1. **Concatenation + shared schema (header=true)** — two files are read as one stream under the header taken
 *    from the first file, with the SECOND file's header row skipped (not emitted as data).
 * 2. **Synthesized positional schema (header=false)** — with no header row, the schema is `c0, c1, …` (from the
 *    first file's first record) and EVERY row across both files is data.
 * 3. **`(fileIndex, position)` migration cursor** — an interrupted run whose first instance parks mid-stream and
 *    hands its cursor to a second instance reconstructs the uninterrupted output exactly (no loss, no
 *    duplication), including crossing the file boundary and re-skipping the next file's header in the resumed
 *    instance. This replicates `JobExecution`'s pause protocol: capture while parked (detaching the open reader),
 *    tear down (onClose skips the detached reader), then load into the rebuilt instance — a single-threaded
 *    `runBlocking` event loop, so the park / capture / cancel handoff is race-free.
 */
class MultiFileReaderWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val selfLocation = ObjectLocation(
        DocumentPath.parse("test/multifile-unit-test.yaml"),
        ObjectPath.parse("main.workers/reader"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun combinesMultipleFilesUnderSharedHeaderSkippingSubsequentHeaders() = runBlocking {
        withTempDir { dir ->
            val fileA = writeLines(dir, "a.csv", "city,amount", "Lviv,10", "Kyiv,20")
            val fileB = writeLines(dir, "b.csv", "city,amount", "Odesa,30", "Dnipro,40")
            val paths = listOf(fileA.toString(), fileB.toString())

            val emitted = mutableListOf<DataRecord>()
            MultiFileReaderWorker(capturingOutput(emitted, 1024), paths, ",", true, selfLocation)
                .run(NoOpJobControl)

            // Both files' data rows, concatenated in order; fileB's header row is NOT emitted.
            assertEquals(
                listOf(
                    listOf("Lviv", "10"),
                    listOf("Kyiv", "20"),
                    listOf("Odesa", "30"),
                    listOf("Dnipro", "40")),
                emitted.map { it.record.toList() })

            // Every record carries the shared schema taken from the first file.
            assertTrue(emitted.all { it.header == HeaderListing.of(listOf("city", "amount")) })
        }
    }


    @Test
    fun synthesizesPositionalSchemaAcrossFilesWhenHeaderless() = runBlocking {
        withTempDir { dir ->
            val fileA = writeLines(dir, "a.csv", "Lviv,10", "Kyiv,20")
            val fileB = writeLines(dir, "b.csv", "Odesa,30")
            val paths = listOf(fileA.toString(), fileB.toString())

            val emitted = mutableListOf<DataRecord>()
            MultiFileReaderWorker(capturingOutput(emitted, 1024), paths, ",", false, selfLocation)
                .run(NoOpJobControl)

            // No header row anywhere: every row across both files is data.
            assertEquals(
                listOf(
                    listOf("Lviv", "10"),
                    listOf("Kyiv", "20"),
                    listOf("Odesa", "30")),
                emitted.map { it.record.toList() })

            // Schema synthesized positionally from the first record's field count.
            assertTrue(emitted.all { it.header == HeaderListing.of(listOf("c0", "c1")) })
        }
    }


    @Test
    fun carriesFileCursorAcrossLiveEditSoResumeContinuesWithoutLossOrDuplication() = runBlocking {
        withTempDir { dir ->
            val fileA = writeLines(dir, "a.csv", "city,amount", "Lviv,10", "Kyiv,20", "Poltava,25")
            val fileB = writeLines(dir, "b.csv", "city,amount", "Odesa,30", "Dnipro,40")
            val paths = listOf(fileA.toString(), fileB.toString())

            // Reference: a single uninterrupted run over the same files.
            val reference = mutableListOf<DataRecord>()
            MultiFileReaderWorker(capturingOutput(reference, 2), paths, ",", true, selfLocation)
                .run(NoOpJobControl)
            val expected = reference.map { it.record.toList() }

            // Interrupted run: instance 1 parks at its second checkpoint (mid fileA), detaches its cursor.
            val emitted1 = mutableListOf<DataRecord>()
            val parked = CompletableDeferred<Unit>()
            val parkForever = CompletableDeferred<Unit>()
            val pausingControl = object: JobControl {
                private var checkpoints = 0
                override suspend fun checkpoint() {
                    checkpoints += 1
                    if (checkpoints >= 2) {
                        parked.complete(Unit)
                        parkForever.await()
                    }
                }
                override suspend fun <R> runBlockingIo(block: () -> R): R = block()
                override fun scratchDir(): String =
                    throw UnsupportedOperationException("A MultiFileReaderWorker needs no scratch dir")
                override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
                override suspend fun host(instructions: ObjectLocation, input: Any?) =
                    throw UnsupportedOperationException("A MultiFileReaderWorker hosts no child")
            }

            val worker1 = MultiFileReaderWorker(capturingOutput(emitted1, 2), paths, ",", true, selfLocation)
            val job = launch { worker1.run(pausingControl) }
            parked.await()

            // Capture while parked (detaches the open reader), then tear down (onClose skips the detached reader).
            val captured = worker1.captureMigrationState()
            job.cancelAndJoin()

            // Instance 2 adopts the cursor and reads the remaining records to completion.
            val emitted2 = mutableListOf<DataRecord>()
            val worker2 = MultiFileReaderWorker(capturingOutput(emitted2, 2), paths, ",", true, selfLocation)
            worker2.loadMigrationState(captured)
            worker2.run(NoOpJobControl)

            // No loss, no duplication: the interrupted run's two halves reconstruct the uninterrupted output.
            val combined = emitted1.map { it.record.toList() } + emitted2.map { it.record.toList() }
            assertEquals(expected, combined)
            assertTrue(emitted1.isNotEmpty(), "instance 1 should have emitted before parking")
            assertTrue(emitted2.isNotEmpty(), "instance 2 should have resumed and emitted")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inline fun <R> withTempDir(use: (Path) -> R): R {
        val dir = Files.createTempDirectory("multifile-reader")
        try {
            return use(dir)
        }
        finally {
            WorkUtils.recursivelyDeleteDir(dir)
        }
    }


    private fun writeLines(dir: Path, name: String, vararg lines: String): Path {
        val file = dir.resolve(name)
        Files.writeString(file, lines.joinToString("\n", postfix = "\n"))
        return file
    }


    private fun capturingOutput(sink: MutableList<DataRecord>, batchSize: Int): ChannelOutput<Any?> =
        object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                sink.add(element as DataRecord)
            }
            override suspend fun flush() {}
            override fun batchSize(): Int = batchSize
            override fun close() {}
        }


    //-----------------------------------------------------------------------------------------------------------------
    // A MultiFileReaderWorker only reads files (through runBlockingIo), checkpoints, and publishes progress.
    private object NoOpJobControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A MultiFileReaderWorker needs no scratch dir")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A MultiFileReaderWorker hosts no child")
    }
}
