package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.channels.Channel
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
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
 * **Framework batching.** Workers emit / consume single logical ELEMENTS (the domain unit, e.g. a
 * [tech.kzen.auto.server.objects.job.worker.DataRecord] or a scalar), but the physical transfer unit is a
 * CHUNK (a `List<Any?>` of up to [chunkSize] elements) — so the per-element coroutine-channel overhead is
 * amortized without any worker hand-rolling batching (the retired `RecordBatch` hack). A producer buffers
 * emitted elements and flushes them as one chunk (see [Producer]); the consumer receives a chunk and yields its
 * elements. Element type is otherwise erased (`Any?`) at run time — the declared `of` / `elementType` is
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
 * everything a channel still holds — carryover not yet delivered, buffered chunks, and any chunk a producer is
 * parked mid-[Producer.flush] on — FLATTENED to elements in delivery order, while the Workers are parked
 * (quiescent) and BEFORE teardown; the rebuilt channel is seeded via [preload]; [input] then delivers that
 * carryover BEFORE the live channel, so the consumer sees the exact same element stream it would have without
 * the migration. (A producer's `pending` buffer is provably EMPTY at any quiescent barrier — the framework
 * flushes it before every checkpoint — so only the parked-mid-flush chunk needs the volatile capture.)
 */
@Reflect
class JobChannel(
    buffer: Int,
    chunk: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    // Elements per physical transfer unit (the batch granularity). At least 1 so a source always makes progress.
    private val chunkSize: Int = chunk.coerceAtLeast(1)

    private val channel: Channel<List<Any?>> =
        if (buffer <= 0) {
            Channel(Channel.RENDEZVOUS)
        }
        else {
            Channel(buffer)
        }

    private val openProducers = AtomicInteger(0)
    private val producers = CopyOnWriteArrayList<Producer>()

    // Count of endpoints (consumers + producers) currently suspended on a channel op: a consumer awaiting the
    // next chunk, or a producer parked on a full buffer. The Job-level deadlock monitor
    // ([tech.kzen.auto.server.exec.job.JobDeadlockMonitor]) sums this across a run's stream channels — when EVERY
    // non-terminal Worker is blocked on a channel, the pipeline can make no progress, so the Job is deadlocked.
    private val blocked = AtomicInteger(0)

    // Elements carried over from a previous instance of this channel across a migration: delivered to the
    // consumer (via input) BEFORE the live channel, so the rebuilt graph resumes the stream without a gap.
    // Seeded by preload before any worker launches; thereafter read only by the single consumer coroutine.
    private val carryover = ArrayDeque<Any?>()


    //-----------------------------------------------------------------------------------------------------------------
    val input: ChannelInput<Any?> = Input()


    fun newProducer(): ChannelOutput<Any?> {
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


    /** Endpoints currently suspended on a channel op (consumers awaiting a chunk + producers on a full buffer). */
    fun blockedCount(): Int {
        return blocked.get()
    }


    // Bracket a suspending channel op so a Worker parked in it counts toward [blockedCount] for the run's
    // deadlock monitor, and stops counting the instant it resumes (a delivered chunk, EOF, or cancellation).
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
    fun preload(items: List<Any?>) {
        carryover.addAll(items)
    }


    /**
     * Snapshot every element this channel still holds, in delivery order, so a migration can carry it into the
     * rebuilt channel rather than dropping it. Called from the run driver while the Workers are parked (paused
     * and quiescent) and BEFORE teardown.
     *
     * Order: not-yet-delivered carryover, then buffered chunks (FIFO), then the chunk a producer is parked
     * mid-[Producer.flush] on (that enters the channel next). A producer's `pending` buffer is NOT captured
     * because it is provably empty at a quiescent barrier: the framework flushes it before every checkpoint, so
     * a parked producer is either at a checkpoint (pending empty) or mid-flush (pending drained into [inFlight]).
     */
    fun drainBuffered(): List<Any?> {
        // Snapshot parked-mid-flush chunks up front (a suspended sender's chunk is NOT in the channel buffer).
        val parkedSends = producers.mapNotNull { it.inFlight }

        val result = ArrayList<Any?>(carryover)
        carryover.clear()

        // Draining the buffer frees space, so a parked sender may resume and move its (same) chunk into the
        // buffer mid-drain; track buffered chunks by identity so such a chunk is counted exactly once.
        val bufferedByIdentity = Collections.newSetFromMap(IdentityHashMap<List<Any?>, Boolean>())
        while (true) {
            val received = channel.tryReceive()
            if (received.isSuccess) {
                val chunk = received.getOrThrow()
                bufferedByIdentity.add(chunk)
                result.addAll(chunk)
            }
            else {
                break
            }
        }

        for (chunk in parkedSends) {
            if (chunk !in bufferedByIdentity) {
                result.addAll(chunk)
            }
        }
        return result
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inner class Producer: ChannelOutput<Any?> {
        // Buffered elements not yet flushed to the channel. Confined to the owning Worker's coroutine (send /
        // flush run there); provably empty whenever the run driver inspects the channel (quiescent barrier), so
        // it needs no cross-thread synchronization.
        private val pending = ArrayList<Any?>()

        // The chunk currently being sent, while a full-channel flush is parked (null otherwise). Read by
        // drainBuffered from the run-driver thread to capture a suspended sender's chunk (not in the buffer).
        @Volatile
        var inFlight: List<Any?>? = null

        private var closed = false


        override fun chunkSize(): Int {
            return chunkSize
        }


        override suspend fun send(element: Any?) {
            pending.add(element)
        }


        override suspend fun flush() {
            if (pending.isEmpty()) {
                return
            }

            // Move the whole buffer into one chunk BEFORE sending: a park mid-send then holds the entire chunk in
            // inFlight (captured by drainBuffered) with pending empty — no partial-buffer capture is needed.
            val chunk = ArrayList<Any?>(pending)
            pending.clear()

            inFlight = chunk
            try {
                tracked { channel.send(chunk) }
            }
            finally {
                inFlight = null
            }
        }


        override fun close() {
            if (! closed) {
                closed = true
                closeOneProducer()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inner class Input: ChannelInput<Any?> {
        // A partially-consumed chunk, when a raw Worker reads element-by-element via receive() / iterator().
        private var held: ArrayDeque<Any?>? = null


        override suspend fun receiveChunk(): List<Any?>? {
            // Drain any partially-consumed held chunk first (if receive() was interleaved on this input).
            held?.let { remaining ->
                if (remaining.isNotEmpty()) {
                    val out = ArrayList<Any?>(remaining)
                    remaining.clear()
                    held = null
                    return out
                }
                held = null
            }

            // Migration carryover is delivered before the live stream, sliced into chunk-sized pieces.
            if (carryover.isNotEmpty()) {
                val n = minOf(chunkSize, carryover.size)
                val out = ArrayList<Any?>(n)
                repeat(n) { out.add(carryover.removeFirst()) }
                return out
            }

            return tracked { channel.receiveCatching().getOrNull() }
        }


        override suspend fun receive(): Any? {
            while (true) {
                val h = held
                if (h != null && h.isNotEmpty()) {
                    return h.removeFirst()
                }
                val chunk = receiveChunk()
                    ?: return null
                held = ArrayDeque(chunk)
            }
        }


        override operator fun iterator(): ChannelInputIterator<Any?> {
            return object: ChannelInputIterator<Any?> {
                private var nextElement: Any? = null
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
                    val chunk = receiveChunk()
                    if (chunk == null) {
                        ended = true
                        return false
                    }
                    held = ArrayDeque(chunk)
                    return hasNext()
                }

                override fun next(): Any? {
                    hasNextElement = false
                    return nextElement
                }
            }
        }
    }
}
