package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.sort.SortColumnSpec
import tech.kzen.auto.common.objects.document.report.spec.sort.SortSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals


/**
 * Unit test for [SortWorker] in isolation: drives the transform's real [SortWorker.run] lifecycle over a fake
 * [ChannelInput] of flat-part [JobMessage]s and a capturing [ChannelOutput], asserting the ordering it emits at
 * end-of-stream.
 *
 * Covers the sort semantics that make it a faithful multi-key operator — single-key ascending / descending,
 * multi-key priority (primary then tie-break), the numeric-before-text total order (numbers compare numerically
 * and sort ahead of lexically-ordered text), STABILITY on equal keys (arrival order preserved), and an empty
 * spec as an arrival-order passthrough — plus the correctness-critical LIVE-EDIT CARRYOVER: a mid-stream
 * interrupt captures the accumulated buffer and a rebuilt instance re-sorts the FULL input, not just the tail a
 * resuming upstream would deliver next.
 */
class SortWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    // HeaderListing.of assigns occurrence 0 to a column's first appearance, so a sort key matches by (text, 0).
    private val header = HeaderListing.of(listOf("city", "amount"))

    private fun record(city: String, amount: String): JobMessage =
        JobMessage.ofFlat(header, FlatFileRecord.of(listOf(city, amount)))

    private fun sortSpec(vararg keys: Pair<String, Boolean>): SortSpec =
        SortSpec(keys.map { SortColumnSpec(HeaderLabel(it.first, 0), it.second) })

    private val selfLocation = ObjectLocation(
        DocumentPath.parse("test/sort-unit-test.yaml"),
        ObjectPath.parse("main.workers/sort"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun singleKeyAscendingOrdersNumericallyByThatColumn() = runBlocking {
        val a = record("a", "30")
        val b = record("b", "10")
        val c = record("c", "20")

        assertEquals(
            listOf(b, c, a),
            runSort(sortSpec("amount" to true), listOf(a, b, c)))
    }


    @Test
    fun singleKeyDescendingReversesTheOrder() = runBlocking {
        val a = record("a", "30")
        val b = record("b", "10")
        val c = record("c", "20")

        assertEquals(
            listOf(a, c, b),
            runSort(sortSpec("amount" to false), listOf(a, b, c)))
    }


    @Test
    fun multiKeySortsByPriorityThenTieBreaksByTheNextKey() = runBlocking {
        val lviv10 = record("Lviv", "10")
        val kyiv20a = record("Kyiv", "20")
        val lviv30 = record("Lviv", "30")
        val kyiv20b = record("Kyiv", "20")

        // Primary: city ascending (text) => Kyiv before Lviv. Secondary: amount descending (numeric).
        // The two Kyiv/20 rows tie on BOTH keys, so the stable sort keeps their arrival order (kyiv20a, kyiv20b).
        assertEquals(
            listOf(kyiv20a, kyiv20b, lviv30, lviv10),
            runSort(sortSpec("city" to true, "amount" to false), listOf(lviv10, kyiv20a, lviv30, kyiv20b)))
    }


    @Test
    fun numbersSortBeforeTextAndEqualTextIsStable() = runBlocking {
        val abc1 = record("p", "abc")
        val ten = record("q", "10")
        val two = record("r", "2")
        val abc2 = record("s", "abc")
        val nine = record("t", "9")

        // Ascending: numeric values first, in numeric order (2, 9, 10); then non-numeric text (abc, abc) in
        // arrival order — so the two "abc" rows stay abc1 before abc2 (stability).
        assertEquals(
            listOf(two, nine, ten, abc1, abc2),
            runSort(sortSpec("amount" to true), listOf(abc1, ten, two, abc2, nine)))
    }


    @Test
    fun emptySortSpecIsAnArrivalOrderPassthrough() = runBlocking {
        val a = record("a", "30")
        val b = record("b", "10")
        val c = record("c", "20")

        assertEquals(listOf(a, b, c), runSort(SortSpec.empty, listOf(a, b, c)))
        assertEquals(listOf(), runSort(sortSpec("amount" to true), listOf()))
    }


    @Test
    fun carriesBufferedInputAcrossLiveEditSoTheRebuiltInstanceSortsTheFullStream() = runBlocking {
        val a = record("a", "30")
        val b = record("b", "10")
        val c = record("c", "20")
        val d = record("d", "5")

        // Drive the first instance over the first half, then interrupt BEFORE end-of-stream (as a pause would)
        // and capture its accumulated buffer — exactly the state the engine carries by stable id on a live edit.
        val first = SortWorker(
            interruptedAfterFirstChunk(listOf(a, b)), discardingOutput, sortSpec("amount" to true), selfLocation)

        val carried: Any =
            try {
                first.run(NoOpJobControl)
                error("SortWorker.run should have been interrupted before end-of-stream")
            }
            catch (signal: PauseSignal) {
                first.captureMigrationState()
            }

        // The rebuilt instance adopts the carried buffer, consumes the rest, and at end-of-stream emits the FULL
        // input sorted — not just the (c, d) tail a resuming upstream delivered.
        val forwarded = mutableListOf<JobMessage>()
        val second = SortWorker(
            chunkedInput(listOf(c, d)), capturingOutput(forwarded), sortSpec("amount" to true), selfLocation)
        second.loadMigrationState(carried)
        second.run(NoOpJobControl)

        // amounts 30, 10, 20, 5 => ascending 5, 10, 20, 30
        assertEquals(listOf(d, b, c, a), forwarded)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runSort(sort: SortSpec, records: List<JobMessage>): List<JobMessage> {
        val forwarded = mutableListOf<JobMessage>()
        val worker = SortWorker(chunkedInput(records), capturingOutput(forwarded), sort, selfLocation)
        worker.run(NoOpJobControl)
        return forwarded
    }


    private fun capturingOutput(sink: MutableList<JobMessage>): ChannelOutput<Any?> =
        object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                sink.add(element as JobMessage)
            }
            override suspend fun flush() {}
            override fun batchSize(): Int = 1024
            override fun close() {}
        }


    // The first (pre-interrupt) instance emits nothing before the cut, so its output is simply discarded.
    private val discardingOutput = object: ChannelOutput<Any?> {
        override suspend fun send(element: Any?) {}
        override suspend fun flush() {}
        override fun batchSize(): Int = 1024
        override fun close() {}
    }


    private fun chunkedInput(records: List<JobMessage>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            // The framework TransformWorker drive loop drains whole chunks: hand it every record as one chunk, then EOF.
            private var delivered = false

            override suspend fun receiveBatch(): List<Any?>? {
                if (delivered || records.isEmpty()) {
                    return null
                }
                delivered = true
                return records
            }

            override suspend fun receive(): Any? = error("unused")
            override fun iterator(): ChannelInputIterator<Any?> = error("unused")
        }


    // Delivers one chunk, then throws on the next receive — simulating a pause cutting the stream mid-flight,
    // BEFORE end-of-stream (so onComplete never runs and the accumulated buffer is intact for capture).
    private fun interruptedAfterFirstChunk(firstChunk: List<JobMessage>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            private var delivered = false

            override suspend fun receiveBatch(): List<Any?>? {
                if (!delivered) {
                    delivered = true
                    return firstChunk
                }
                throw PauseSignal()
            }

            override suspend fun receive(): Any? = error("unused")
            override fun iterator(): ChannelInputIterator<Any?> = error("unused")
        }


    private class PauseSignal: RuntimeException()


    //-----------------------------------------------------------------------------------------------------------------
    // A SortWorker only consumes + emits + checkpoints + publishes; none of those need coordination in isolation.
    private object NoOpJobControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A SortWorker needs no scratch dir")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A SortWorker hosts no child")
    }
}
