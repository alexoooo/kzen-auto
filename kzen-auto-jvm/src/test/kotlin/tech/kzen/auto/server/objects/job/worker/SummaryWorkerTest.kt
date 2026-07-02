package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
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
import kotlin.test.assertTrue


/**
 * Unit test for [SummaryWorker] in isolation: drives the transform's full [SummaryWorker.run] lifecycle over a
 * fake [ChannelInput] of [DataRecord]s and a capturing [ChannelOutput], asserting the two things that make it a
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
            DataRecord(header, FlatFileRecord.of(listOf("alice", "30"))),
            DataRecord(header, FlatFileRecord.of(listOf("bob", "40"))),
            DataRecord(header, FlatFileRecord.of(listOf("alice", "50"))))

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


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runSummary(records: List<DataRecord>): Pair<List<Any?>, TableSummary> {
        val forwarded = mutableListOf<Any?>()
        val output = object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                forwarded.add(element)
            }
            override suspend fun flush() {}
            override fun chunkSize(): Int = 1024
            override fun close() {}
        }

        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/summary-unit-test.yaml"),
            ObjectPath.parse("main.workers/summary"))

        val worker = SummaryWorker(chunkedInput(records), output, emptyServer, selfLocation)
        worker.run(NoOpJobControl)

        // captureMigrationState() exposes the same accumulated state the serve / progress snapshot reads.
        val accumulation = worker.captureMigrationState()
        return forwarded to accumulation.tableSummary()
    }


    private fun chunkedInput(records: List<DataRecord>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            // The framework TransformWorker drive loop drains whole chunks: hand it every record as one chunk, then EOF.
            private var delivered = false

            override suspend fun receiveChunk(): List<Any?>? {
                if (delivered || records.isEmpty()) {
                    return null
                }
                delivered = true
                return records
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
    // A SummaryWorker only consumes + emits + checkpoints + publishes; none of those need coordination in isolation.
    private object NoOpJobControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A SummaryWorker needs no scratch dir")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A SummaryWorker hosts no child")
    }
}
