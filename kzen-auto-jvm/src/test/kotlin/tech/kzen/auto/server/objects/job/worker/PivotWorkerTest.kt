package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueColumnSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.api.ChannelServerIterator
import tech.kzen.auto.common.paradigm.job.api.ServedRequest
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.server.objects.report.exec.output.pivot.PivotBuilder
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * Unit test for [PivotWorker]: driven through its real [PivotWorker.run] lifecycle over a fake input of
 * flat-backed values, it asserts the two things that make it a faithful, self-cleaning pivot operator —
 *
 * 1. **A/B parity** — the pivot table it emits downstream is row-for-row identical, under the same output header,
 *    to what a direct [PivotBuilder] produces over the same records (the P4d A/B gate). This also transitively
 *    exercises the serve path: [PivotWorker.onQuery] answers a preview query with the SAME `PivotBuilder.preview`
 *    call the emit path pages through, and the duplex serve LOOP itself is framework-owned (covered end-to-end
 *    for [PreviewWorker] by JobExecutionTest).
 * 2. **Scratch sweep** — the H2-backed scratch dir the Worker opens is closed-then-deleted once the run settles
 *    ([PivotWorker.onClose]).
 */
class PivotWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val header = HeaderListing.of(listOf("city", "amount"))

    private fun record(city: String, amount: String): DataValue =
        JobDataValues.flat(header, FlatFileRecord.of(listOf(city, amount)))

    // Group by city; aggregate amount as Sum + Count.
    private fun pivotSpec(): PivotSpec =
        PivotSpec(
            HeaderListing.of(listOf("city")),
            PivotValueTableSpec(mapOf(
                HeaderLabel("amount", 0) to PivotValueColumnSpec(setOf(
                    PivotValueType.Sum, PivotValueType.Count)))))

    private val selfLocation = ObjectLocation(
        DocumentPath.parse("test/pivot-unit-test.yaml"),
        ObjectPath.parse("main.workers/pivot"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emitsPivotTableMatchingDirectPivotBuilderAndSweepsScratchDir() = runBlocking {
        val records = listOf(
            record("Lviv", "10"),
            record("Kyiv", "20"),
            record("Lviv", "30"))
        val pivot = pivotSpec()

        // Reference: a direct PivotBuilder over the same records.
        val (expectedHeader, expectedRows) = withDirectPivot(pivot, records) { direct ->
            val preview = direct.preview(pivot.values, 0L, 100)
            preview.renderedHeader to preview.rows
        }

        // The Worker, driven through its real run() lifecycle with a real scratch dir.
        val workerScratch = Files.createTempDirectory("pivot-worker-scratch")
        val forwarded = mutableListOf<DataValue>()

        val worker = PivotWorker(
            chunkedInput(listOf(records)), capturingOutput(forwarded), emptyServer, pivot, selfLocation)
        worker.run(ScratchJobControl(workerScratch))

        // A/B: the emitted pivot rows match the direct builder's, under the same output header.
        assertEquals(expectedRows, forwarded.map { testRecord(it).toList() })
        assertTrue(forwarded.isNotEmpty())
        assertTrue(forwarded.all { testProjection(it).header == HeaderListing.of(expectedHeader) })

        // The scratch dir is closed-then-deleted once the run settles.
        assertFalse(Files.exists(workerScratch))
    }


    // The progress wire contract lock with PreviewWorkerDisplay (job-worker.yaml assigns it as this Worker's
    // display): every push must parse the way the display does — a numeric total under the count key, a list of
    // strings under the header key, a list of rows under the rows key. Periodic (non-forced) pushes carry a
    // bounded teaser page of the live pivot; the final forced push carries the full table (up to the query
    // limit) — the same rows the Worker emits downstream.
    @Test
    fun progressPushesParseAsPreviewDisplayExpectsWithBoundedTeaser() = runBlocking {
        // 15 distinct cities -> 15 pivot rows, exceeding the teaser bound.
        val records = (1..15).map { record("city$it", it.toString()) }
        val pivot = pivotSpec()

        val workerScratch = Files.createTempDirectory("pivot-worker-progress")
        val forwarded = mutableListOf<DataValue>()
        val control = ScratchJobControl(workerScratch)

        val worker = PivotWorker(
            chunkedInput(records.chunked(5)), capturingOutput(forwarded), emptyServer, pivot, selfLocation)
        worker.run(control)

        assertTrue(control.progressPushes.isNotEmpty())
        for ((push, force) in control.progressPushes) {
            // PreviewWorkerDisplay's parse semantics: longValue(count), parseHeader, parseRows.
            assertTrue(push[JobConventions.progressCountKey] is Long)
            val header = push[JobConventions.progressHeaderKey] as List<*>
            assertTrue(header.isNotEmpty() && header.all { it is String })
            val rows = push[JobConventions.progressRowsKey] as List<*>
            assertTrue(rows.all { it is List<*> })

            if (!force) {
                assertTrue(rows.size <= JobConventions.progressTeaserRowCount)
            }
        }

        // The final forced push carries the whole pivot: the same rows emitted downstream, and the total count.
        val (finalPush, finalForce) = control.progressPushes.last()
        assertTrue(finalForce)
        assertEquals(15L, finalPush[JobConventions.progressCountKey])
        assertEquals(
            forwarded.map { testRecord(it).toList() },
            finalPush[JobConventions.progressRowsKey])
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Runs `use` against a direct PivotBuilder built from `records` in a throwaway dir, then closes-and-deletes it.
    private fun <R> withDirectPivot(pivot: PivotSpec, records: List<DataValue>, use: (PivotBuilder) -> R): R {
        val dir = Files.createTempDirectory("pivot-direct")
        try {
            return PivotBuilder
                .create(pivot.rows, HeaderListing(pivot.values.columns.keys.toList()), dir)
                .use { direct ->
                    records.forEach { direct.add(testRecord(it), testProjection(it).header) }
                    use(direct)
                }
        }
        finally {
            WorkUtils.recursivelyDeleteDir(dir)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun chunkedInput(chunks: List<List<DataValue>>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            private var next = 0

            override suspend fun receiveBatch(): List<Any?>? {
                if (next >= chunks.size) {
                    return null
                }
                return chunks[next++]
            }

            override suspend fun receive(): Any? = error("unused")
            override fun iterator(): ChannelInputIterator<Any?> = error("unused")
        }


    private fun capturingOutput(sink: MutableList<DataValue>): ChannelOutput<Any?> =
        object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                sink.add(testJobValue(element))
            }
            override suspend fun flush() {}
            override fun batchSize(): Int = 1024
            override fun close() {}
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
    // Returns a real, created scratch dir; a PivotWorker opens its H2 stores under it and deletes it in onClose.
    // The progress pushes (value + force) are recorded so the wire contract can be asserted; bypasses
    // EngineJobControl's throttle, so every push lands.
    private class ScratchJobControl(private val scratchDir: Path): JobControl {
        val progressPushes = mutableListOf<Pair<Map<String, Any?>, Boolean>>()

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()

        override fun scratchDir(): String {
            Files.createDirectories(scratchDir)
            return scratchDir.toString()
        }

        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {
            progressPushes.add(value to force)
        }
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A PivotWorker hosts no child")
    }
}
