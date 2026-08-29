package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.channels.Channel
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.exec.data.value.DataValue
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
 * BATCH (a `List<DataValue>` of up to [batchSize] elements) — so the per-element coroutine-channel overhead is
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
 * **Migration carryover:** a state migration (pause / edit config / continue) tears the running graph down and
 * rebuilds it, so the in-flight elements a live channel holds would otherwise be lost. [drainBuffered] snapshots
 * everything a channel still holds — carryover not yet delivered, buffered batches, and any batch a producer is
 * parked mid-[Producer.flush] on — FLATTENED to elements in delivery order, while the Workers are parked
 * (quiescent) and BEFORE teardown; the rebuilt channel is seeded via [preload]; [input] then delivers that
 * carryover BEFORE the live channel, so the consumer sees the exact same element stream it would have without
 * the migration. (A producer's `pending` buffer is provably EMPTY at any quiescent barrier — the framework
 * flushes it before every checkpoint — so only the parked-mid-flush batch needs the volatile capture.)
 */
@Reflect
class JobChannel(
    capacity: Int,
    batchSize: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    // Elements per physical transfer unit (the batch granularity). At least 1 so a source always makes progress.
    private val batchSize: Int = batchSize.coerceAtLeast(1)

    private val channel: Channel<List<DataValue>> =
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

    // Elements carried over from a previous instance of this channel across a migration: delivered to the
    // consumer (via input) BEFORE the live channel, so the rebuilt graph resumes the stream without a gap.
    // Seeded by preload before any worker launches; thereafter read only by the single consumer coroutine.
    private val carryover = ArrayDeque<DataValue>()


    //-----------------------------------------------------------------------------------------------------------------
    val input: ChannelInput<DataValue> = Input()


    fun newProducer(): ChannelOutput<DataValue> {
        openProducers.incrementAndGet()
        val producer = Producer()
        producers.add(producer)
        return producer
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
    // Seed the carryover (elements) drained from a previous instance of this channel (by stable id) during a
    // migration. Called from the run driver before any worker launches, so no consumer is reading yet.
    fun preload(items: List<DataValue>) {
        carryover.addAll(items)
    }


    /**
     * Snapshot every element this channel still holds, in delivery order, so a migration can carry it into the
     * rebuilt channel rather than dropping it. Called from the run driver while the Workers are parked (paused
     * and quiescent) and BEFORE teardown.
     *
     * Order: not-yet-delivered carryover, then buffered batches (FIFO), then the batch a producer is parked
     * mid-[Producer.flush] on (that enters the channel next). A producer's `pending` buffer is NOT captured
     * because it is provably empty at a quiescent barrier: the framework flushes it before every checkpoint, so
     * a parked producer is either at a checkpoint (pending empty) or mid-flush (pending drained into [inFlight]).
     */
    fun drainBuffered(): List<DataValue> {
        // Snapshot parked-mid-flush batches up front (a suspended sender's batch is NOT in the channel buffer).
        val parkedSends = producers.mapNotNull { it.inFlight }

        val result = ArrayList<DataValue>(carryover)
        carryover.clear()

        // Draining the buffer frees space, so a parked sender may resume and move its (same) batch into the
        // buffer mid-drain; track buffered batches by identity so such a batch is counted exactly once.
        val bufferedByIdentity = Collections.newSetFromMap(IdentityHashMap<List<DataValue>, Boolean>())
        while (true) {
            val received = channel.tryReceive()
            if (received.isSuccess) {
                val batch = received.getOrThrow()
                bufferedByIdentity.add(batch)
                result.addAll(batch)
            }
            else {
                break
            }
        }

        for (batch in parkedSends) {
            if (batch !in bufferedByIdentity) {
                result.addAll(batch)
            }
        }
        return result
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inner class Producer: ChannelOutput<DataValue> {
        // Buffered elements not yet flushed to the channel. Confined to the owning Worker's coroutine (send /
        // flush run there); provably empty whenever the run driver inspects the channel (quiescent barrier), so
        // it needs no cross-thread synchronization.
        private val pending = ArrayList<DataValue>()

        // The batch currently being sent, while a full-channel flush is parked (null otherwise). Read by
        // drainBuffered from the run-driver thread to capture a suspended sender's batch (not in the buffer).
        @Volatile
        var inFlight: List<DataValue>? = null

        private var closed = false


        override fun batchSize(): Int {
            return batchSize
        }


        override suspend fun send(element: DataValue) {
            pending.add(element)
        }


        override suspend fun flush() {
            if (pending.isEmpty()) {
                return
            }

            // Move the whole buffer into one batch BEFORE sending: a park mid-send then holds the entire batch in
            // inFlight (captured by drainBuffered) with pending empty — no partial-buffer capture is needed.
            val batch = ArrayList<DataValue>(pending)
            pending.clear()

            inFlight = batch
            try {
                tracked { channel.send(batch) }
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
    private inner class Input: ChannelInput<DataValue> {
        // A partially-consumed batch, when a raw Worker reads element-by-element via receive() / iterator().
        private var held: ArrayDeque<DataValue>? = null


        override suspend fun receiveBatch(): List<DataValue>? {
            // Drain any partially-consumed held batch first (if receive() was interleaved on this input).
            held?.let { remaining ->
                if (remaining.isNotEmpty()) {
                    val out = ArrayList<DataValue>(remaining)
                    remaining.clear()
                    held = null
                    return out
                }
                held = null
            }

            // Migration carryover is delivered before the live stream, sliced into batch-sized pieces.
            if (carryover.isNotEmpty()) {
                val n = minOf(batchSize, carryover.size)
                val out = ArrayList<DataValue>(n)
                repeat(n) { out.add(carryover.removeFirst()) }
                return out
            }

            return tracked { channel.receiveCatching().getOrNull() }
        }


        override suspend fun receive(): DataValue? {
            while (true) {
                val h = held
                if (h != null && h.isNotEmpty()) {
                    return h.removeFirst()
                }
                val batch = receiveBatch()
                    ?: return null
                held = ArrayDeque(batch)
            }
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
                    val h = held
                    if (h != null && h.isNotEmpty()) {
                        nextElement = h.removeFirst()
                        hasNextElement = true
                        return true
                    }
                    val batch = receiveBatch()
                    if (batch == null) {
                        ended = true
                        return false
                    }
                    held = ArrayDeque(batch)
                    return hasNext()
                }

                override fun next(): DataValue {
                    hasNextElement = false
                    return checkNotNull(nextElement)
                }
            }
        }
    }
}
