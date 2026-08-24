package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.api.ChannelServerIterator
import tech.kzen.auto.common.paradigm.job.api.ServedRequest
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.output.flat.IndexedCsvTable
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * Unit test for [ExploreWorker]: driven through its real [ExploreWorker.run] lifecycle over a fake input of
 * flat-part [JobMessage]s, it asserts the two things that make it a faithful, PERSISTENT browse operator —
 *
 * 1. **Serve A/B parity** — a random-access slice query (`offset` / `limit`) answered by the Worker's serve path
 *    is byte-identical to what a direct [IndexedCsvTable] produces over the same records and slice (the P4g
 *    gate). Because [ExploreWorker] is a SINK (it emits nothing downstream), there is no output stream to compare
 *    against — so unlike [PivotWorkerTest] this drives the ACTUAL serve loop: one request is fed through the
 *    framework-owned [tech.kzen.auto.server.objects.job.worker.WorkerBase] serve coroutine and answered while the
 *    table is fully populated. The two coroutines are coordinated by a pair of [CompletableDeferred]s so the
 *    query lands deterministically after every record is indexed and before the run settles (a single-threaded
 *    `runBlocking` event loop, so the handoff is race-free).
 * 2. **Output persistence** — the file-backed output dir the Worker opens is flushed-and-closed but KEPT once the
 *    run settles ([ExploreWorker.onClose]), so the result (`table.csv`) stays on disk to be browsed / downloaded
 *    after the run ends. This is the core of making a Job usable for reporting — the data must NOT vanish on
 *    settle.
 *
 * The accumulated row count pushed to the trace is checked as a bonus (the final forced progress publish).
 */
class ExploreWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val header = HeaderListing.of(listOf("city", "amount"))

    private fun record(city: String, amount: String): JobMessage =
        JobMessage.ofFlat(header, FlatFileRecord.of(listOf(city, amount)))

    private val records = listOf(
        record("Lviv", "10"),
        record("Kyiv", "20"),
        record("Lviv", "30"),
        record("Odesa", "40"))

    private val selfLocation = ObjectLocation(
        DocumentPath.parse("test/explore-unit-test.yaml"),
        ObjectPath.parse("main.workers/explore"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun servesRandomAccessSliceMatchingDirectIndexedCsvTableAndPersistsResult() = runBlocking {
        // A middle slice (skip the first row, take the next two) — a non-trivial random-access window.
        val offset = 1L
        val limit = 2

        // Reference: the same slice from a direct IndexedCsvTable over the same records (structural equality holds
        // — ExecutionSuccess / ExecutionValue are data classes all the way down).
        val expected = withDirectTable(records) { direct ->
            ExecutionSuccess.ofValue(ExecutionValue.of(direct.preview(offset, limit).asCollection()))
        }

        val workerScratch = Files.createTempDirectory("explore-worker-scratch")

        // One served slice request, delivered to the real serve loop once the table is fully populated.
        val request = ExecutionRequest(
            RequestParams.of(
                JobConventions.previewOffsetParameter to offset.toString(),
                JobConventions.previewLimitParameter to limit.toString()),
            null)

        val readyForServe = CompletableDeferred<Unit>()
        val serveAnswered = CompletableDeferred<Unit>()
        var served: ExecutionResult? = null

        val server = oneShotServer(request, readyForServe) { reply ->
            served = reply
            serveAnswered.complete(Unit)
        }
        val input = coordinatedInput(records, readyForServe, serveAnswered)

        val control = ScratchJobControl(workerScratch)
        val worker = ExploreWorker(input, server, selfLocation)
        try {
            worker.run(control)

            // A/B: the served slice matches the direct IndexedCsvTable's preview exactly.
            assertEquals(expected, served)

            // The running row count reached the trace (under the shared count key, matching PreviewWorker — so
            // the client parses it as JobWorkerProgress.rowCount and can gate the download link on there
            // being data).
            assertEquals(
                records.size.toLong(),
                control.progressValues.last()[JobConventions.progressCountKey])

            // The output dir and its table.csv PERSIST once the run settles (NOT swept) — so the result stays
            // browsable / downloadable after the run ends.
            assertTrue(Files.exists(workerScratch))
            assertTrue(Files.exists(workerScratch.resolve(IndexedCsvTable.tableFile)))
        }
        finally {
            // The Worker's output is persistent (it never deletes its own dir) — the test cleans up.
            if (Files.exists(workerScratch)) {
                WorkUtils.recursivelyDeleteDir(workerScratch)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Runs `use` against a direct IndexedCsvTable built from `records` in a throwaway dir, then closes-and-deletes it.
    private fun <R> withDirectTable(records: List<JobMessage>, use: (IndexedCsvTable) -> R): R {
        val dir = Files.createTempDirectory("explore-direct")
        try {
            val table = IndexedCsvTable(records.first().flat!!.header, dir)
            try {
                records.forEach { table.add(it.flat!!.record, it.flat!!.header) }
                return use(table)
            }
            finally {
                table.close(error = false)
            }
        }
        finally {
            WorkUtils.recursivelyDeleteDir(dir)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Delivers the records as one chunk, then — on the next pull, once they are all indexed and the snapshot is
    // published — releases the serve loop to answer its query and blocks until it has, so the query is guaranteed
    // to land against the fully populated table before the stream ends.
    private fun coordinatedInput(
        records: List<JobMessage>,
        readyForServe: CompletableDeferred<Unit>,
        serveAnswered: CompletableDeferred<Unit>
    ): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            private var delivered = false

            override suspend fun receiveBatch(): List<Any?>? {
                if (! delivered) {
                    delivered = true
                    return records
                }
                readyForServe.complete(Unit)
                serveAnswered.await()
                return null
            }

            override suspend fun receive(): Any? = error("unused")
            override fun iterator(): ChannelInputIterator<Any?> = error("unused")
        }


    // A serve channel that delivers exactly one request — but only after [readyForServe] fires (the table is
    // populated) — then ends, so the framework's serve loop answers once and exits.
    private fun oneShotServer(
        servedRequest: ExecutionRequest,
        readyForServe: CompletableDeferred<Unit>,
        onReply: (ExecutionResult) -> Unit
    ): ChannelServer<Any?, Any?> =
        object: ChannelServer<Any?, Any?> {
            override suspend fun receive(): ServedRequest<Any?, Any?>? = error("unused")

            override fun iterator(): ChannelServerIterator<Any?, Any?> =
                object: ChannelServerIterator<Any?, Any?> {
                    private var consumed = false

                    override suspend fun hasNext(): Boolean {
                        if (consumed) {
                            return false
                        }
                        readyForServe.await()
                        return true
                    }

                    override fun next(): ServedRequest<Any?, Any?> {
                        consumed = true
                        return object: ServedRequest<Any?, Any?> {
                            override val request: Any = servedRequest
                            override fun reply(response: Any?) {
                                onReply(response as ExecutionResult)
                            }
                        }
                    }
                }
        }


    //-----------------------------------------------------------------------------------------------------------------
    // Hands the ExploreWorker its persistent output dir; the Worker opens its IndexedCsvTable under it, clears it
    // at onStart, and KEEPS it at onClose (the test cleans it up). Captures published progress for the row count.
    private class ScratchJobControl(private val outputDir: Path): JobControl {
        val progressValues = mutableListOf<Map<String, Any?>>()

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()

        // Persistent, per-Worker output dir — NOT auto-created (the Worker manages it), mirroring EngineJobControl.
        override fun outputDir(): String {
            return outputDir.toString()
        }

        override fun scratchDir(): String =
            throw UnsupportedOperationException("An ExploreWorker uses outputDir, not scratchDir")

        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {
            progressValues.add(value)
        }

        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("An ExploreWorker hosts no child")
    }
}
