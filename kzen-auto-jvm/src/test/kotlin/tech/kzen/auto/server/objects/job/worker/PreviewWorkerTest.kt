package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.api.ChannelServerIterator
import tech.kzen.auto.common.paradigm.job.api.ServedRequest
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * Unit test for [PreviewWorker]'s SCALAR (payload) lane in isolation: drives the sink's full [PreviewWorker.run]
 * lifecycle over a fake [ChannelInput] of payload [JobMessage]s — the shape a FormulaSource / Run
 * pipeline emits — and asserts each auto-flattens ([JobMessage.flatView]) to a single `value` column. The
 * record lane and the duplex serve path are already covered end-to-end by
 * [tech.kzen.auto.server.objects.job.JobExecutionTest]; this isolates the payload lane so one Preview view
 * serves both.
 */
class PreviewWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val selfLocation = ObjectLocation(
        DocumentPath.parse("test/preview-unit-test.yaml"),
        ObjectPath.parse("main.workers/preview"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun scalarElementsRenderAsSingleValueColumn() = runBlocking {
        val snapshot = runPreview(listOf("1", "2", "Fizz", 4, "Buzz"))
        assertEquals(listOf("value"), snapshot.header)
        assertEquals(5L, snapshot.count)
        assertEquals(
            listOf(listOf("1"), listOf("2"), listOf("Fizz"), listOf("4"), listOf("Buzz")),
            snapshot.rows)
    }


    @Test
    fun nullScalarRendersAsNullText() = runBlocking {
        // ColumnValue.toText's canonical null rendering (the same "null" an expression column would show).
        val snapshot = runPreview(listOf(null, "x"))
        assertEquals(listOf("value"), snapshot.header)
        assertEquals(listOf(listOf("null"), listOf("x")), snapshot.rows)
    }


    // The progress wire contract PreviewWorkerDisplay renders from: periodic (non-forced) pushes carry only a
    // bounded teaser of the most recent rows, while the final forced push keeps the full window for the
    // post-run card. Keys locked via the shared JobConventions constants so the two sides can't drift.
    @Test
    fun periodicProgressIsBoundedTeaserWhileFinalPushKeepsFullWindow() = runBlocking {
        val elements = (1..25).map { it.toString() }
        val expectedRows = elements.map { listOf(it) }

        val worker = PreviewWorker(scalarInput(elements.chunked(10)), emptyServer, 1000, selfLocation)
        val control = RecordingJobControl()
        worker.run(control)

        // One non-forced push per input batch, bounded to the teaser tail of the rolling window.
        val periodic = control.progressPushes.filter { !it.second }.map { it.first }
        assertEquals(3, periodic.size)
        for (push in periodic) {
            val rows = push[JobConventions.progressRowsKey] as List<*>
            assertTrue(rows.size <= JobConventions.progressTeaserRowCount)
        }
        // The teaser is the most RECENT rows: after the second batch (20 elements), rows 11..20.
        assertEquals(expectedRows.subList(10, 20), periodic[1][JobConventions.progressRowsKey])
        assertEquals(20L, periodic[1][JobConventions.progressCountKey])

        // The final forced push keeps the full window (what survives on the trace for the post-run card).
        val (finalPush, finalForce) = control.progressPushes.last()
        assertTrue(finalForce)
        assertEquals(listOf("value"), finalPush[JobConventions.progressHeaderKey])
        assertEquals(expectedRows, finalPush[JobConventions.progressRowsKey])
        assertEquals(25L, finalPush[JobConventions.progressCountKey])
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runPreview(elements: List<Any?>): PreviewWorker.Snapshot {
        val worker = PreviewWorker(scalarInput(listOf(elements)), emptyServer, 1000, selfLocation)
        worker.run(RecordingJobControl())

        // captureMigrationState() returns the same immutable snapshot() the trace / serve paths read.
        return worker.captureMigrationState() as PreviewWorker.Snapshot
    }


    private fun scalarInput(chunks: List<List<Any?>>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            // The framework SinkWorker drive loop drains whole chunks: hand it each chunk in turn (each raw
            // value wrapped as a payload message, as a source's Emitter would), then EOF.
            private var next = 0

            override suspend fun receiveBatch(): List<Any?>? {
                if (next >= chunks.size) {
                    return null
                }
                return chunks[next++].map { JobMessage.ofPayload(it) }
            }

            override suspend fun receive(): Any? = error("unused")

            override fun iterator(): ChannelInputIterator<Any?> = error("unused")
        }


    // A serve channel that ends immediately, so the framework's serve loop exits without serving any request.
    private val emptyServer = object: ChannelServer<Any?, Any?> {
        override suspend fun receive(): ServedRequest<Any?, Any?>? = null

        override fun iterator(): ChannelServerIterator<Any?, Any?> =
            object: ChannelServerIterator<Any?, Any?> {
                override suspend fun hasNext(): Boolean = false
                override fun next(): ServedRequest<Any?, Any?> = error("no requests")
            }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A sink Preview only consumes + checkpoints + publishes; the progress pushes (value + force) are recorded
    // so the wire contract can be asserted. Bypasses EngineJobControl's throttle, so every push is captured.
    private class RecordingJobControl: JobControl {
        val progressPushes = mutableListOf<Pair<Map<String, Any?>, Boolean>>()

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A PreviewWorker needs no scratch dir")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {
            progressPushes.add(value to force)
        }
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A PreviewWorker hosts no child")
    }
}
