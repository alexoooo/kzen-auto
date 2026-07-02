package tech.kzen.auto.common.paradigm.job.api


/**
 * Producer endpoint of a one-way Job Channel. A Worker emits single logical ELEMENTS via [send]; the framework
 * transparently BATCHES them into chunks (the channel's configured [chunkSize] — see
 * [tech.kzen.auto.server.objects.job.channel.JobChannel]) for the actual cross-Worker transfer, so a Worker
 * never hand-rolls batching (the old `RecordBatch` list is gone). [send] only BUFFERS — it never blocks; the
 * accumulated elements are handed to the channel by [flush], which applies backpressure (suspending while the
 * channel is full) and is called by the framework at safe boundaries (a source per [chunkSize], a transform at
 * each input-chunk boundary) so a paused Worker strands no buffered element.
 *
 * A Channel may have several producer endpoints (fan-in merge); consumers see end-of-stream only once *all* of
 * them have closed (close-on-last-producer), so a single producer's [close] is not premature EOF.
 */
interface ChannelOutput<in T> {
    /**
     * Buffers [element] for the next [flush]. Never suspends on backpressure (buffering is unbounded between
     * flushes — the framework flushes often enough to bound it): backpressure is applied by [flush].
     */
    suspend fun send(element: T)


    /**
     * Sends everything [send] has buffered since the last flush as a single chunk, suspending under
     * backpressure while the channel is full. A no-op when nothing is buffered.
     */
    suspend fun flush()


    /** The channel's configured elements-per-chunk — the batch granularity the framework flushes a source at. */
    fun chunkSize(): Int


    /** Signals this producer endpoint is finished. Idempotent. Does NOT flush (the framework flushes first). */
    fun close()
}
