package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.server.objects.job.value.recycle.Recyclable
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.engine.resource.Hold
import tech.kzen.lib.common.exec.engine.resource.Holder
import tech.kzen.lib.common.exec.engine.resource.ResourceRegistry
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
 * **Consumer close (content streaming spike CS2):** a consumer that completes early closes its end through
 * [FrameworkChannelInput.closeConsumer]; from then on a producer's `send` / `flush` fails with
 * [DownstreamClosedException] (a parked one is resumed with it) and what the buffer holds is released. That is
 * the only way a producer can tell a CLOSED downstream from a PAUSED one; without it the producer would stay
 * parked behind the finished consumer.
 *
 * **Resources (E9).** Once [bindResources] has named the run's resource registry and this channel's holder, a
 * producer's `send` is the transport-transfer boundary: a value that depends on a resource (or a Worker-created
 * `AutoCloseable` the run does not own yet, adopted right there) takes one channel hold per send, held until the
 * consumer is done with the element — a framework drive loop converts it into its per-callback hold
 * ([FrameworkChannelInput]); the raw `receive` / `iterator` / SPI `receiveBatch` paths release the previously
 * handed-out element(s) on each further pull and at end-of-stream. An owned element never waits in a producer's
 * buffer: it flushes at once, together with whatever unowned elements were buffered before it, so an item whose
 * close releases the host's next permit cannot deadlock behind the batch it belongs to (channel capacity is
 * unchanged). A send that fails releases the batch's holds — a failed send closes what it adopted — except under
 * cancellation, where the run's teardown (or a migration's carryover) owns them. Unbound (no registry), the
 * channel behaves as before. A pooled element is an owned element like any other: its channel hold is one more
 * hold on its pool resource, and the last release recycles it. A raw read forfeits it as it is handed out, since a
 * raw reader is untrusted to drop what it read by its next pull.
 *
 * **Migration carryover:** a state migration (pause / edit config / continue) tears the running graph down and
 * rebuilds it, so the in-flight elements a live channel holds would otherwise be lost. [drainBuffered] snapshots
 * everything a channel still holds — the not-yet-dispatched remainder of a framework loop's active batch, a raw
 * reader's partially consumed batch, carryover not yet delivered, buffered batches, and any batch a producer is
 * parked mid-[Producer.flush] on — FLATTENED to elements in delivery order with their channel holds, while the
 * Workers are parked (quiescent) and BEFORE teardown; the rebuilt channel is seeded via [preload] and holds the
 * same holds (no close, no re-adoption); [input] then delivers that carryover BEFORE the live channel, so the
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

    private val capacity: Int = capacity.coerceAtLeast(0)

    private val channel: Channel<Batch> =
        Channel(if (capacity <= 0) Channel.RENDEZVOUS else capacity, onUndeliveredElement = ::undelivered)

    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val downstreamClosedMessage =
            "Downstream of this channel has completed; it receives nothing more"
    }


    private val openProducers = AtomicInteger(0)
    private val producers = CopyOnWriteArrayList<Producer>()

    // Set once the consumer closed its end (see FrameworkChannelInput.closeConsumer): producers then fail with
    // DownstreamClosedException instead of buffering or parking.
    @Volatile
    private var consumerClosed = false

    // Set as the last producer closes, before the consumer is resumed with the end of the stream.
    @Volatile
    private var producersClosed = false


    // A batch the transport took from its sender that no receiver will ever see: the receiver was cancelled as
    // the batch was handed to it, or the sender was cancelled while parked (prompt cancellation, delivered or
    // not). Its holds are let go here — unless a migration barrier's drain already made them the carryover's.
    private fun undelivered(batch: Batch) {
        if (!batch.carried) {
            Hold.releaseAll(batch.holds)
        }
    }

    // Elements sitting in the channel's buffer (batches sent, not yet received); maintained around each send /
    // receive, so momentarily approximate.
    private val queued = AtomicInteger(0)

    // Elements carried over from a previous instance of this channel across a migration: delivered to the
    // consumer (via input) BEFORE the live channel, so the rebuilt graph resumes the stream without a gap.
    // Seeded by preload before any worker launches; thereafter read only by the single consumer coroutine.
    private val carryover = ArrayDeque<Carried>()

    // The run's resource registry and this channel's holder, once JobRun has bound them (null in a bare channel).
    @Volatile
    private var resources: Resources? = null


    //-----------------------------------------------------------------------------------------------------------------
    val input: ChannelInput<DataValue> = Input()


    fun newProducer(): ChannelOutput<DataValue> {
        openProducers.incrementAndGet()
        val producer = Producer()
        producers.add(producer)
        return producer
    }


    /** Names the run's resource registry and this channel's holder (E9): from here on, sends take channel holds. */
    fun bindResources(registry: ResourceRegistry, holder: Holder) {
        resources = Resources(registry, holder)
    }


    private fun closeOneProducer() {
        if (openProducers.decrementAndGet() <= 0) {
            producersClosed = true
            channel.close()
        }
    }


    /**
     * Elements this channel currently holds: buffered batches, a parked-mid-flush batch, undelivered carryover,
     * and the not-yet-dispatched remainder of the consumer's active batch. Approximate under concurrency.
     */
    fun queuedElements(): Int {
        val parked = producers.sumOf { it.inFlight?.size ?: 0 }
        return queued.get() + parked + carryover.size + (input as Input).undeliveredCount()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Seed the carryover (elements with their channel holds) drained from a previous instance of this channel
    // (by stable id) during a migration. Called from the run driver before any worker launches, so no consumer is
    // reading yet.
    fun preload(items: ChannelCarryover) {
        for (index in items.elements.indices) {
            carryover.addLast(Carried(items.elements[index], items.hold(index)))
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
        val holds = ArrayList<Hold?>()
        fun add(batch: Batch) {
            batch.carried = true
            elements.addAll(batch.elements)
            holds.addAll(batch.holds)
        }

        (input as Input).drainUndelivered(elements, holds)
        for (carried in carryover) {
            elements.add(carried.element)
            holds.add(carried.hold)
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
        return ChannelCarryover(elements, holds)
    }


    /**
     * Lets go of every channel hold still outstanding once the run is over (not migrating): what a raw reader
     * was handed and never pulled past, and everything [drainBuffered] would capture. The elements are dropped.
     */
    fun releaseHeld() {
        (input as Input).releaseRaw()
        drainBuffered().release()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class Resources(
        val registry: ResourceRegistry,
        val holder: Holder
    )


    // One physical transfer unit: the elements and, index-aligned, the channel hold each owned element carries.
    private class Batch(
        val elements: List<DataValue>,
        val holds: List<Hold?>
    ) {
        val size: Int
            get() = elements.size

        // Set by drainBuffered (run-driver thread, at a migration barrier or the run's teardown) once the holds
        // are the carryover's: the transport dropping this batch (see undelivered) must then not release them.
        @Volatile
        var carried = false

        /** Releases every hold; a close failure is suppressed on [primary], which stays the failure reported. */
        fun releaseHolds(primary: Throwable) {
            try {
                Hold.releaseAll(holds)
            }
            catch (e: Exception) {
                primary.addSuppressed(e)
            }
        }
    }


    private class Carried(
        val element: DataValue,
        val hold: Hold?
    )


    //-----------------------------------------------------------------------------------------------------------------
    private inner class Producer: ChannelOutput<DataValue>, FrameworkChannelOutput {
        // Buffered elements (and their channel holds) not yet flushed to the channel. Confined to the owning
        // Worker's coroutine (send / flush run there); provably empty whenever the run driver inspects the
        // channel (quiescent barrier), so it needs no cross-thread synchronization.
        private val pending = ArrayList<DataValue>()
        private val pendingHolds = ArrayList<Hold?>()

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
            if (consumerClosed) {
                throw downstreamClosed()
            }
            // The transport-transfer boundary (E9): the channel's hold is taken here, before whoever handed the
            // value over lets go of theirs, so the count never touches zero mid-hop.
            val held = resources?.let { it.registry.hold(element, it.holder) }
            pending.add(held?.value ?: element)
            pendingHolds.add(held?.hold)
            if (held != null) {
                flush()
            }
        }


        override suspend fun flush() {
            if (pending.isEmpty()) {
                return
            }

            // Move the whole buffer into one batch BEFORE sending: a park mid-send then holds the entire batch in
            // inFlight (captured by drainBuffered) with pending empty — no partial-buffer capture is needed.
            val batch = Batch(ArrayList(pending), ArrayList(pendingHolds))
            pending.clear()
            pendingHolds.clear()

            inFlight = batch
            try {
                if (!channel.trySend(batch).isSuccess) {
                    parked = true
                    channel.send(batch)
                }
                queued.addAndGet(batch.size)
            }
            catch (e: CancellationException) {
                // A sender cancelled while parked: the transport hands the batch back through undelivered, which
                // lets its holds go (or leaves them to the carryover that captured the parked batch)
                throw e
            }
            catch (e: DownstreamClosedException) {
                // The consumer closed while this batch was buffered or parked: nobody will receive it, so its
                // holds are released here and the producer learns the downstream is gone (a fresh instance —
                // the one the close resumed a parked send with is the closer's, shared by every sender)
                val closed = downstreamClosed()
                batch.releaseHolds(closed)
                throw closed
            }
            catch (e: Throwable) {
                batch.releaseHolds(e)
                throw e
            }
            finally {
                inFlight = null
            }
        }


        private fun downstreamClosed(): DownstreamClosedException =
            DownstreamClosedException(downstreamClosedMessage)


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

        // Channel holds of the element(s) a raw path handed out most recently: released on the next pull and
        // at end-of-stream — the consumer is done with them by then.
        private val rawOutstanding = ArrayList<Hold>()

        // The framework drive loop's active batch, until it is fully dispatched or detached; its remainder is
        // captured by drainBuffered.
        @Volatile
        private var active: ReceivedBatch? = null


        override suspend fun receiveFrameworkBatch(): ReceivedBatch? {
            active = null
            val batch = nextBatch()
                ?: return null
            val received = ReceivedBatch(batch.elements, batch.holds) { active = null }
            active = received
            return received
        }


        override fun closeConsumer() {
            if (consumerClosed) {
                return
            }
            consumerClosed = true
            // Closing WITH a cause fails the channel for send: a producer already suspended on a full channel
            // is resumed with the cause, a later send throws it, and a producer's own close stays a no-op.
            // What the buffer already holds is released here — no consumer will ever receive it.
            channel.close(DownstreamClosedException(downstreamClosedMessage))
            releaseRaw()
            while (true) {
                val batch = channel.tryReceive().getOrNull()
                    ?: break
                queued.addAndGet(-batch.size)
                Hold.releaseAll(batch.holds)
            }
        }


        override suspend fun receiveBatch(): List<DataValue>? {
            releaseRaw()
            val batch = nextBatch()
                ?: return null
            for (index in batch.elements.indices) {
                batch.holds[index]?.let { handOut(batch.elements[index], it) }
            }
            return batch.elements
        }


        override suspend fun receive(): DataValue? {
            releaseRaw()
            val carried = nextElement()
                ?: return null
            carried.hold?.let { handOut(carried.element, it) }
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
                    carried.hold?.let { handOut(carried.element, it) }
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


        fun drainUndelivered(elements: MutableList<DataValue>, holds: MutableList<Hold?>) {
            active?.let { batch ->
                val remaining = batch.remaining()
                elements.addAll(remaining.elements)
                for (index in remaining.elements.indices) {
                    holds.add(remaining.hold(index))
                }
                batch.detach()
            }
            held?.let { remaining ->
                for (carried in remaining) {
                    elements.add(carried.element)
                    holds.add(carried.hold)
                }
                remaining.clear()
                held = null
            }
        }


        // A raw reader is untrusted to drop what it read by its next pull, so a pooled element is forfeited
        // as it is handed out; its channel hold is then released like any other
        private fun handOut(element: DataValue, hold: Hold) {
            element.owners?.entries()?.forEach { (it.native as? Recyclable)?.forfeit() }
            rawOutstanding.add(hold)
        }


        fun releaseRaw() {
            if (rawOutstanding.isEmpty()) {
                return
            }
            val outstanding = ArrayList(rawOutstanding)
            rawOutstanding.clear()
            Hold.releaseAll(outstanding)
        }


        private suspend fun nextElement(): Carried? {
            while (true) {
                val h = held
                if (h != null && h.isNotEmpty()) {
                    return h.removeFirst()
                }
                val batch = nextBatch()
                    ?: return null
                held = ArrayDeque(batch.elements.indices.map { Carried(batch.elements[it], batch.holds[it]) })
            }
        }


        private suspend fun nextBatch(): Batch? {
            // Drain any partially-consumed held batch first (if receive() was interleaved on this input).
            held?.let { remaining ->
                if (remaining.isNotEmpty()) {
                    val out = Batch(remaining.map { it.element }, remaining.map { it.hold })
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
                val holds = ArrayList<Hold?>(n)
                repeat(n) {
                    val carried = carryover.removeFirst()
                    elements.add(carried.element)
                    holds.add(carried.hold)
                }
                return Batch(elements, holds)
            }

            val batch = channel.receiveCatching().getOrNull()
                ?: return null
            queued.addAndGet(-batch.size)
            return batch
        }
    }
}
