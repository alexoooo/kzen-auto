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
 * Element type is erased (`Any?`) at runtime — the declared `of` type is for authoring/wiring only — so a
 * single non-generic class instantiates cleanly via `@Reflect`; the typed [ChannelInput] / [ChannelOutput]
 * views are what workers see (injected by `JobChannelCreator`).
 *
 * **Close-on-last-producer:** a fan-in channel may have several producer endpoints; consumers see
 * end-of-stream only once *all* of them have [ChannelOutput.close]d, so one producer finishing is not a
 * premature EOF. The live producer count is tracked across worker threads via an [AtomicInteger].
 *
 * **Migration carryover:** a state migration (pause / edit config / continue) tears the running graph down and
 * rebuilds it, so the in-flight payloads a live channel holds would otherwise be lost — and because a
 * [tech.kzen.auto.server.objects.job.worker.CsvReaderWorker] resumes from its file position rather than
 * re-reading, that loss is permanent. To keep migration lossless, [drainBuffered] snapshots everything a
 * channel still holds (buffered payloads plus any a producer is parked mid-[send] on) before teardown, and the
 * rebuilt channel is seeded via [preload]; [input] then delivers that carryover BEFORE the live channel, so the
 * consumer sees the exact same stream it would have without the migration.
 */
@Reflect
class JobChannel(
    buffer: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val channel: Channel<Any?> =
        if (buffer <= 0) {
            Channel(Channel.RENDEZVOUS)
        }
        else {
            Channel(buffer)
        }

    private val openProducers = AtomicInteger(0)
    private val producers = CopyOnWriteArrayList<Producer>()

    // Count of endpoints (consumers + producers) currently suspended on a channel op: a consumer awaiting the
    // next payload, or a producer parked on a full buffer. The Job-level deadlock monitor
    // ([tech.kzen.auto.server.exec.job.JobDeadlockMonitor]) sums this across a run's stream channels — when EVERY
    // non-terminal Worker is blocked on a channel, the pipeline can make no progress (a lone sink on an orphan
    // channel, or a set of Workers each waiting on the other), so the Job is deadlocked. Kept self-contained here
    // rather than fed to an injected shared object, so a channel stays constructible by its `@Reflect` ctor.
    private val blocked = AtomicInteger(0)

    // Payloads carried over from a previous instance of this channel across a migration: delivered to the
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


    /** Endpoints currently suspended on a channel op (consumers awaiting input + producers on a full buffer). */
    fun blockedCount(): Int {
        return blocked.get()
    }


    // Bracket a suspending channel op so a Worker parked in it counts toward [blockedCount] for the run's
    // deadlock monitor, and stops counting the instant it resumes (a delivered payload, EOF, or cancellation).
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
    // Seed the carryover drained from a previous instance of this channel (by stable id) during a migration.
    // Called from the run driver before any worker launches, so no consumer is reading yet.
    fun preload(items: List<Any?>) {
        carryover.addAll(items)
    }


    /**
     * Snapshot everything this channel still holds, in delivery order, so a migration can carry it into the
     * rebuilt channel rather than dropping it. Called from the run driver while the Workers are parked (paused
     * and quiescent) and BEFORE teardown — the only safe point, since teardown cancels the producers (losing
     * any payload one is parked mid-[send] on).
     *
     * Order is: not-yet-delivered carryover, then buffered payloads (FIFO), then payloads a producer is parked
     * mid-send on (those enter the channel last). A producer's parked payload is captured explicitly because a
     * suspended sender's element is NOT in the buffer; [Producer.inFlight] is snapshotted up front (before the
     * drain below resumes any sender) and deduplicated by identity against the drained buffer, so a sender that
     * happens to resume mid-drain — moving its element into the buffer — is still counted exactly once.
     */
    fun drainBuffered(): List<Any?> {
        val parkedSends = producers.mapNotNull { it.inFlight }

        val buffered = ArrayList<Any?>(carryover)
        carryover.clear()

        while (true) {
            val received = channel.tryReceive()
            if (received.isSuccess) {
                buffered.add(received.getOrThrow())
            }
            else {
                break
            }
        }

        if (parkedSends.isEmpty()) {
            return buffered
        }

        val drainedByIdentity = Collections.newSetFromMap(IdentityHashMap<Any?, Boolean>())
        drainedByIdentity.addAll(buffered)

        val result = ArrayList<Any?>(buffered.size + parkedSends.size)
        result.addAll(buffered)
        for (payload in parkedSends) {
            if (payload !in drainedByIdentity) {
                result.add(payload)
            }
        }
        return result
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inner class Producer: ChannelOutput<Any?> {
        private var closed = false

        // The payload currently being sent, while a full-channel send is parked (null otherwise). Read by
        // drainBuffered from the run-driver thread to capture a suspended sender's element (not in the buffer).
        @Volatile
        var inFlight: Any? = null

        override suspend fun send(payload: Any?) {
            inFlight = payload
            try {
                tracked { channel.send(payload) }
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


    private inner class Input: ChannelInput<Any?> {
        override suspend fun receive(): Any? {
            if (carryover.isNotEmpty()) {
                return carryover.removeFirst()
            }
            return tracked { channel.receiveCatching().getOrNull() }
        }

        override operator fun iterator(): ChannelInputIterator<Any?> {
            val delegate = channel.iterator()
            return object: ChannelInputIterator<Any?> {
                override suspend fun hasNext(): Boolean {
                    if (carryover.isNotEmpty()) {
                        return true
                    }
                    return tracked { delegate.hasNext() }
                }

                override fun next(): Any? {
                    if (carryover.isNotEmpty()) {
                        return carryover.removeFirst()
                    }
                    return delegate.next()
                }
            }
        }
    }
}
