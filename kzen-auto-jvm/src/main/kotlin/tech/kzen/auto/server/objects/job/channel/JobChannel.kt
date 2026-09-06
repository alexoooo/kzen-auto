package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.auto.server.exec.job.ownership.LeaseHolder
import tech.kzen.auto.server.exec.job.ownership.RunOwnershipLedger
import tech.kzen.auto.server.exec.job.ownership.ValueLeases
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.reflect.Reflect
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger


/**
 * The shared, first-class conduit a Job `Channel` notation object instantiates: one-way streaming over a
 * [kotlinx.coroutines.channels.Channel], with the consumer endpoint exposed as [input] and each producer
 * endpoint handed out by [newProducer].
 *
 * **Framework batching.** Workers emit / consume single logical ELEMENTS (the domain unit, a
 * [DataValue]), but the physical transfer unit is a
 * BATCH (up to [batchSize] elements) — so the per-element coroutine-channel overhead is
 * amortized without any worker hand-rolling batching (the retired `RecordBatch` hack). A producer buffers
 * emitted elements and flushes them as one batch (see [Producer]); the consumer receives a batch and yields its
 * elements. The declared `of` / `elementType` remains
 * authoring/wiring metadata validated by
 * [tech.kzen.auto.common.objects.document.job.ChannelTypeDefiner] — so a single non-generic class instantiates
 * cleanly via `@Reflect`.
 *
 * **Close-on-last-producer:** a fan-in channel may have several producer endpoints; consumers see end-of-stream
 * only once *all* of them have [ChannelOutput.close]d. The live producer count is tracked across worker threads
 * via an [AtomicInteger].
 *
 * **Ownership (E9).** Once [bindOwnership] has named the run's ledger and this channel's holder, a producer's
 * `send` is the transport-transfer boundary: a value the run owns (or a Worker-created `AutoCloseable` the run
 * does not own yet, adopted right there) takes one channel lease per send, held until the consumer is done with
 * the element — a framework drive loop converts it into its per-callback lease ([FrameworkChannelInput]); the
 * raw `receive` / `iterator` / SPI `receiveBatch` paths release the previously handed-out element(s) on each
 * further pull and at end-of-stream. An owned element never waits in a producer's buffer: it flushes at once,
 * together with whatever unowned elements were buffered before it, so an item whose close releases the host's
 * next permit cannot deadlock behind the batch it belongs to (channel capacity is unchanged). A send that fails
 * releases the batch's leases — a failed send closes what it adopted — except under cancellation, where the
 * run's teardown (or a migration's carryover) owns them. Unbound (no ledger), the channel behaves as before.
 *
 * **Migration carryover:** a state migration (pause / edit config / continue) tears the running graph down and
 * rebuilds it, so the in-flight elements a live channel holds would otherwise be lost. [drainBuffered] snapshots
 * everything a channel still holds — the not-yet-dispatched remainder of a framework loop's active batch, a raw
 * reader's partially consumed batch, carryover not yet delivered, buffered batches, and any batch a producer is
 * parked mid-[Producer.flush] on — FLATTENED to elements in delivery order with their channel leases, while the
 * Workers are parked (quiescent) and BEFORE teardown; the rebuilt channel is seeded via [preload] and holds the
 * same leases (no close, no re-adoption); [input] then delivers that carryover BEFORE the live channel, so the
 * consumer sees the exact same element stream it would have without the migration. (A producer's `pending`
 * buffer is provably EMPTY at any quiescent barrier — the framework flushes it before every checkpoint, and an
 * owned element flushes at send — so only the parked-mid-flush batch needs the volatile capture.)
 */
@Reflect
class JobChannel(
    capacity: Int,
    batchSize: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    // Elements per physical transfer unit (the batch granularity). At least 1 so a source always makes progress.
    private val batchSize: Int = batchSize.coerceAtLeast(1)

    private val channel: Channel<Batch> =
        if (capacity <= 0) {
            Channel(Channel.RENDEZVOUS)
        }
        else {
            Channel(capacity)
        }

    private val openProducers = AtomicInteger(0)
    private val producers = CopyOnWriteArrayList<Producer>()

    // Count of endpoints (consumers + producers) currently suspended on a channel op: a consumer awaiting the
    // next batch, or a producer parked on a full channel. The Job-level deadlock monitor
    // ([tech.kzen.auto.server.exec.job.JobDeadlockMonitor]) sums this across a run's stream channels — when EVERY
    // non-terminal Worker is blocked on a channel, the pipeline can make no progress, so the Job is deadlocked.
    private val blocked = AtomicInteger(0)

    // Elements sitting in the channel's buffer (batches sent, not yet received) — the occupancy the run's
    // diagnostics report (E9 item 4/5); maintained around each send / receive, so momentarily approximate.
    private val queued = AtomicInteger(0)

    // Elements handed to the consumer so far: with the ledger's activity, the run's progress clock.
    private val transferred = AtomicInteger(0)

    // Elements carried over from a previous instance of this channel across a migration: delivered to the
    // consumer (via input) BEFORE the live channel, so the rebuilt graph resumes the stream without a gap.
    // Seeded by preload before any worker launches; thereafter read only by the single consumer coroutine.
    private val carryover = ArrayDeque<Carried>()

    // The run's ledger and this channel's lease holder, once JobRun has bound them (null in a bare channel).
    @Volatile
    private var ownership: Ownership? = null


    //-----------------------------------------------------------------------------------------------------------------
    val input: ChannelInput<DataValue> = Input()


    fun newProducer(): ChannelOutput<DataValue> {
        openProducers.incrementAndGet()
        val producer = Producer()
        producers.add(producer)
        return producer
    }


    /** Names the run's ledger and this channel's holder (E9): from here on, sends take channel leases. */
    fun bindOwnership(ledger: RunOwnershipLedger, holder: LeaseHolder) {
        ownership = Ownership(ledger, holder)
    }


    private fun closeOneProducer() {
        if (openProducers.decrementAndGet() <= 0) {
            channel.close()
        }
    }


    /** Endpoints currently suspended on a channel op (consumers awaiting a batch + producers on a full channel). */
    fun blockedCount(): Int {
        return blocked.get()
    }


    /** Elements the consumer has received so far (a progress clock for the run's stall detection). */
    fun transferredElements(): Int {
        return transferred.get()
    }


    /**
     * Elements this channel currently holds: buffered batches, a parked-mid-flush batch, undelivered carryover,
     * and the not-yet-dispatched remainder of the consumer's active batch. Approximate under concurrency.
     */
    fun queuedElements(): Int {
        val parked = producers.sumOf { it.inFlight?.size ?: 0 }
        return queued.get() + parked + carryover.size + (input as Input).undeliveredCount()
    }


    // Bracket a suspending channel op so a Worker parked in it counts toward [blockedCount] for the run's
    // deadlock monitor, and stops counting the instant it resumes (a delivered batch, EOF, or cancellation).
    private suspend fun <R> tracked(await: suspend () -> R): R {
        blocked.incrementAndGet()
        try {
            return await()
        }
        finally {
            blocked.decrementAndGet()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Seed the carryover (elements with their channel leases) drained from a previous instance of this channel
    // (by stable id) during a migration. Called from the run driver before any worker launches, so no consumer is
    // reading yet.
    fun preload(items: ChannelCarryover) {
        for (index in items.elements.indices) {
            carryover.addLast(Carried(items.elements[index], items.lease(index)))
        }
    }


    /**
     * Snapshot every element this channel still holds, in delivery order, so a migration can carry it into the
     * rebuilt channel rather than dropping it. Called from the run driver while the Workers are parked (paused
     * and quiescent) and BEFORE teardown.
     *
     * Order: the consumer's undelivered remainders (a framework loop's active batch past its dispatched index,
     * then a raw reader's partially consumed batch), not-yet-delivered carryover, then buffered batches (FIFO),
     * then the batch a producer is parked mid-[Producer.flush] on (that enters the channel next). A producer's
     * `pending` buffer is NOT captured because it is provably empty at a quiescent barrier: the framework flushes
     * it before every checkpoint, so a parked producer is either at a checkpoint (pending empty) or mid-flush
     * (pending drained into [Producer.inFlight]).
     */
    fun drainBuffered(): ChannelCarryover {
        // Snapshot parked-mid-flush batches up front (a suspended sender's batch is NOT in the channel buffer).
        val parkedSends = producers.mapNotNull { it.inFlight }

        val elements = ArrayList<DataValue>()
        val leases = ArrayList<ValueLease?>()
        fun add(batch: Batch) {
            elements.addAll(batch.elements)
            leases.addAll(batch.leases)
        }

        (input as Input).drainUndelivered(elements, leases)
        for (carried in carryover) {
            elements.add(carried.element)
            leases.add(carried.lease)
        }
        carryover.clear()

        // Draining the buffer frees space, so a parked sender may resume and move its (same) batch into the
        // buffer mid-drain; track buffered batches by identity so such a batch is counted exactly once.
        val bufferedByIdentity = Collections.newSetFromMap(IdentityHashMap<Batch, Boolean>())
        while (true) {
            val received = channel.tryReceive()
            if (received.isSuccess) {
                val batch = received.getOrThrow()
                queued.addAndGet(-batch.size)
                bufferedByIdentity.add(batch)
                add(batch)
            }
            else {
                break
            }
        }

        for (batch in parkedSends) {
            if (batch !in bufferedByIdentity) {
                add(batch)
            }
        }
        return ChannelCarryover(elements, leases)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class Ownership(
        val ledger: RunOwnershipLedger,
        val holder: LeaseHolder
    )


    // One physical transfer unit: the elements and, index-aligned, the channel lease each owned element carries.
    private class Batch(
        val elements: List<DataValue>,
        val leases: List<ValueLease?>
    ) {
        val size: Int
            get() = elements.size

        fun releaseLeases(primary: Throwable) {
            ValueLeases.releaseAllSuppressed(leases, primary)
        }
    }


    private class Carried(
        val element: DataValue,
        val lease: ValueLease?
    )


    //-----------------------------------------------------------------------------------------------------------------
    private inner class Producer: ChannelOutput<DataValue>, FrameworkChannelOutput {
        // Buffered elements (and their channel leases) not yet flushed to the channel. Confined to the owning
        // Worker's coroutine (send / flush run there); provably empty whenever the run driver inspects the
        // channel (quiescent barrier), so it needs no cross-thread synchronization.
        private val pending = ArrayList<DataValue>()
        private val pendingLeases = ArrayList<ValueLease?>()

        // The batch currently being sent, while a full-channel flush is parked (null otherwise). Read by
        // drainBuffered from the run-driver thread to capture a suspended sender's batch (not in the buffer).
        @Volatile
        var inFlight: Batch? = null

        private var closed = false

        // Whether a flush parked on a full channel since the Emitter last asked: the Worker then checkpoints
        // at once, so a migration's drain never unparks it into producing more (see FrameworkChannelOutput).
        private var parked = false


        override fun batchSize(): Int {
            return batchSize
        }


        override fun takeParked(): Boolean {
            val result = parked
            parked = false
            return result
        }


        override suspend fun send(element: DataValue) {
            // The transport-transfer boundary (E9): the channel's hold is taken here, before whoever handed the
            // value over lets go of theirs, so the count never touches zero mid-hop.
            val lease = ownership?.let { it.ledger.hold(element, it.holder) }
            pending.add(element)
            pendingLeases.add(lease)
            if (lease != null) {
                flush()
            }
        }


        override suspend fun flush() {
            if (pending.isEmpty()) {
                return
            }

            // Move the whole buffer into one batch BEFORE sending: a park mid-send then holds the entire batch in
            // inFlight (captured by drainBuffered) with pending empty — no partial-buffer capture is needed.
            val batch = Batch(ArrayList(pending), ArrayList(pendingLeases))
            pending.clear()
            pendingLeases.clear()

            inFlight = batch
            try {
                if (!channel.trySend(batch).isSuccess) {
                    parked = true
                    tracked { channel.send(batch) }
                }
                queued.addAndGet(batch.size)
            }
            catch (e: CancellationException) {
                // Delivered or not, the batch is the teardown's (or, at a migration barrier, the carryover's)
                throw e
            }
            catch (e: Throwable) {
                batch.releaseLeases(e)
                throw e
            }
            finally {
                inFlight = null
            }
        }


        override fun close() {
            if (!closed) {
                closed = true
                closeOneProducer()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inner class Input: ChannelInput<DataValue>, FrameworkChannelInput {
        // A partially-consumed batch, when a raw Worker reads element-by-element via receive() / iterator().
        @Volatile
        private var held: ArrayDeque<Carried>? = null

        // Channel leases of the element(s) a raw path handed out most recently: released on the next pull and
        // at end-of-stream — the consumer is done with them by then.
        private val rawOutstanding = ArrayList<ValueLease>()

        // The framework drive loop's active batch, until it is fully dispatched or detached; its remainder is
        // captured by drainBuffered.
        @Volatile
        private var active: ReceivedBatch? = null


        override suspend fun receiveFrameworkBatch(): ReceivedBatch? {
            active = null
            val batch = nextBatch()
                ?: return null
            transferred.addAndGet(batch.size)
            val received = ReceivedBatch(batch.elements, batch.leases) { active = null }
            active = received
            return received
        }


        override suspend fun receiveBatch(): List<DataValue>? {
            releaseRaw()
            val batch = nextBatch()
                ?: return null
            transferred.addAndGet(batch.size)
            batch.leases.filterNotNullTo(rawOutstanding)
            return batch.elements
        }


        override suspend fun receive(): DataValue? {
            releaseRaw()
            val carried = nextElement()
                ?: return null
            carried.lease?.let { rawOutstanding.add(it) }
            return carried.element
        }


        override operator fun iterator(): ChannelInputIterator<DataValue> {
            return object: ChannelInputIterator<DataValue> {
                private var nextElement: DataValue? = null
                private var hasNextElement = false
                private var ended = false

                override suspend fun hasNext(): Boolean {
                    if (hasNextElement) {
                        return true
                    }
                    if (ended) {
                        return false
                    }
                    releaseRaw()
                    val carried = nextElement()
                    if (carried == null) {
                        ended = true
                        return false
                    }
                    carried.lease?.let { rawOutstanding.add(it) }
                    nextElement = carried.element
                    hasNextElement = true
                    return true
                }

                override fun next(): DataValue {
                    hasNextElement = false
                    return checkNotNull(nextElement)
                }
            }
        }


        // Elements received but not yet handed to a Worker: the framework loop's remainder and the raw held batch.
        fun undeliveredCount(): Int =
            (active?.remainingCount() ?: 0) + (held?.size ?: 0)


        fun drainUndelivered(elements: MutableList<DataValue>, leases: MutableList<ValueLease?>) {
            active?.let { batch ->
                val remaining = batch.remaining()
                elements.addAll(remaining.elements)
                for (index in remaining.elements.indices) {
                    leases.add(remaining.lease(index))
                }
                batch.detach()
            }
            held?.let { remaining ->
                for (carried in remaining) {
                    elements.add(carried.element)
                    leases.add(carried.lease)
                }
                remaining.clear()
                held = null
            }
        }


        private fun releaseRaw() {
            if (rawOutstanding.isEmpty()) {
                return
            }
            val outstanding = ArrayList(rawOutstanding)
            rawOutstanding.clear()
            ValueLeases.releaseAll(outstanding)
        }


        private suspend fun nextElement(): Carried? {
            while (true) {
                val h = held
                if (h != null && h.isNotEmpty()) {
                    return h.removeFirst()
                }
                val batch = nextBatch()
                    ?: return null
                transferred.addAndGet(batch.size)
                held = ArrayDeque(batch.elements.indices.map { Carried(batch.elements[it], batch.leases[it]) })
            }
        }


        private suspend fun nextBatch(): Batch? {
            // Drain any partially-consumed held batch first (if receive() was interleaved on this input).
            held?.let { remaining ->
                if (remaining.isNotEmpty()) {
                    val out = Batch(remaining.map { it.element }, remaining.map { it.lease })
                    remaining.clear()
                    held = null
                    return out
                }
                held = null
            }

            // Migration carryover is delivered before the live stream, sliced into batch-sized pieces.
            if (carryover.isNotEmpty()) {
                val n = minOf(batchSize, carryover.size)
                val elements = ArrayList<DataValue>(n)
                val leases = ArrayList<ValueLease?>(n)
                repeat(n) {
                    val carried = carryover.removeFirst()
                    elements.add(carried.element)
                    leases.add(carried.lease)
                }
                return Batch(elements, leases)
            }

            val batch = tracked { channel.receiveCatching().getOrNull() }
                ?: return null
            queued.addAndGet(-batch.size)
            return batch
        }
    }
}
