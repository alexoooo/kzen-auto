package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.summary.StatisticValueSummary
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.api.ChannelServerIterator
import tech.kzen.auto.common.paradigm.job.api.ServedRequest
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * Unit test for [SummaryWorker] in isolation: drives the transform's full [SummaryWorker.run] lifecycle over a
 * fake [ChannelInput] of flat-part [JobMessage]s and a capturing [ChannelOutput], asserting the two things that make it a
 * live analytics operator — (1) it PASSES every record through downstream unchanged (it composes into a
 * pipeline), and (2) its accumulated [TableSummary] matches the per-column stats / histogram the reused
 * [tech.kzen.auto.server.objects.report.exec.summary.model.ValueSummaryBuilder] engine computes. The duplex
 * serve loop itself is framework-owned (covered end-to-end by
 * [tech.kzen.auto.server.objects.job.JobExecutionTest] for [PreviewWorker]); here we assert the summary CONTENT
 * that loop would serve, and that it serializes for the reply.
 */
class SummaryWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val header = HeaderListing.of(listOf("name", "age"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun passesRecordsThroughUnchangedWhileSummarizingPerColumn() = runBlocking {
        val records = listOf(
            JobMessage.ofFlat(header, FlatFileRecord.of(listOf("alice", "30"))),
            JobMessage.ofFlat(header, FlatFileRecord.of(listOf("bob", "40"))),
            JobMessage.ofFlat(header, FlatFileRecord.of(listOf("alice", "50"))))

        val (forwarded, summary) = runSummary(records)

        // Passthrough: every input record continues downstream unchanged (same instances, same order).
        assertEquals(records, forwarded)

        val byColumn = summary.columnSummaries.map.entries.associate { it.key.text to it.value }
        assertEquals(setOf("name", "age"), byColumn.keys)

        // "age" is numeric: count / sum / min / max over 30, 40, 50.
        assertEquals(
            StatisticValueSummary(3, 120.0, 30.0, 50.0),
            byColumn.getValue("age").numericValueSummary)

        // The summary serializes for the duplex serve reply (onQuery) without hitting an unsupported-type path.
        ExecutionValue.of(summary.toCollection())

        // "name" is nominal: the value histogram is alice x2, bob x1.
        assertEquals(
            mapOf("alice" to 2L, "bob" to 1L),
            byColumn.getValue("name").nominalValueSummary.histogram)
    }


    @Test
    fun emptyStreamYieldsEmptySummaryAndNoOutput() = runBlocking {
        val (forwarded, summary) = runSummary(listOf())
        assertEquals(listOf(), forwarded)
        assertTrue(summary.isEmpty())
    }


    // The progress wire contract SummaryWorkerDisplay renders from: periodic (non-forced) pushes carry only the
    // running row count (push is a teaser — the full TableSummary would grow engine history without bound),
    // while the final forced push adds the complete accumulated summary for the post-run card.
    @Test
    fun periodicProgressIsCountOnlyWhileFinalPushCarriesFullSummary() = runBlocking {
        val records = (1..25).map {
            JobMessage.ofFlat(header, FlatFileRecord.of(listOf("name$it", it.toString())))
        }

        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/summary-unit-test.yaml"),
            ObjectPath.parse("main.workers/summary"))

        val worker = SummaryWorker(
            chunkedInput(records.chunked(10)), discardingOutput(), emptyServer, selfLocation)
        val control = RecordingJobControl()
        worker.run(control)

        // One non-forced push per input batch, each count-only.
        val periodic = control.progressPushes.filter { !it.second }.map { it.first }
        assertEquals(3, periodic.size)
        for (push in periodic) {
            assertTrue(JobConventions.progressCountKey in push)
            assertFalse(JobConventions.progressSummaryKey in push)
        }
        assertEquals(20L, periodic[1][JobConventions.progressCountKey])

        // The final forced push carries the full accumulated summary (what the post-run card renders).
        val (finalPush, finalForce) = control.progressPushes.last()
        assertTrue(finalForce)
        assertEquals(25L, finalPush[JobConventions.progressCountKey])
        assertEquals(
            worker.captureMigrationState().tableSummary().toCollection(),
            finalPush[JobConventions.progressSummaryKey])
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runSummary(records: List<JobMessage>): Pair<List<Any?>, TableSummary> {
        val forwarded = mutableListOf<Any?>()
        val output = object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                forwarded.add(element)
            }
            override suspend fun flush() {}
            override fun batchSize(): Int = 1024
            override fun close() {}
        }

        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/summary-unit-test.yaml"),
            ObjectPath.parse("main.workers/summary"))

        val worker = SummaryWorker(chunkedInput(listOf(records)), output, emptyServer, selfLocation)
        worker.run(RecordingJobControl())

        // captureMigrationState() exposes the same accumulated state the serve / progress snapshot reads.
        val accumulation = worker.captureMigrationState()
        return forwarded to accumulation.tableSummary()
    }


    private fun discardingOutput(): ChannelOutput<Any?> =
        object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {}
            override suspend fun flush() {}
            override fun batchSize(): Int = 1024
            override fun close() {}
        }


    private fun chunkedInput(chunks: List<List<JobMessage>>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            // The framework TransformWorker drive loop drains whole chunks: hand it each chunk in turn, then EOF.
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
    // A SummaryWorker only consumes + emits + checkpoints + publishes; the progress pushes (value + force) are
    // recorded so the wire contract can be asserted. Bypasses EngineJobControl's throttle, so every push lands.
    private class RecordingJobControl: JobControl {
        val progressPushes = mutableListOf<Pair<Map<String, Any?>, Boolean>>()

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A SummaryWorker needs no scratch dir")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {
            progressPushes.add(value to force)
        }
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A SummaryWorker hosts no child")
    }
}
