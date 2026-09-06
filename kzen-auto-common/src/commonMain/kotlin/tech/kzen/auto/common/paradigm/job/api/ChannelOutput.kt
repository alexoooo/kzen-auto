package tech.kzen.auto.common.paradigm.job.api


/**
 * Producer endpoint of a one-way Job Channel. A Worker emits single logical ELEMENTS via [send]; the framework
 * transparently GROUPS them into batches (the channel's configured [batchSize] — see
 * [tech.kzen.auto.server.objects.job.channel.JobChannel]) for the actual cross-Worker transfer, so a Worker
 * never hand-rolls batching (the old `RecordBatch` list is gone). [send] only BUFFERS an ordinary element; the
 * accumulated elements are handed to the channel by [flush], which applies backpressure (suspending while the
 * channel is full) and is called by the framework at safe boundaries (a source per [batchSize], a transform at
 * each input-batch boundary) so a paused Worker strands no buffered element. An element the run owns (a value
 * carrying an `AutoCloseable` the run will close) is flushed the moment it is sent — together with whatever
 * was buffered before it — so [send] may suspend under backpressure for it; the channel's own capacity is
 * unchanged.
 *
 * A Channel may have several producer endpoints (fan-in merge); consumers see end-of-stream only once *all* of
 * them have closed (close-on-last-producer), so a single producer's [close] is not premature EOF.
 */
interface ChannelOutput<in T> {
    /**
     * Buffers [element] for the next [flush] — an unowned element never suspends on backpressure (buffering is
     * unbounded between flushes; the framework flushes often enough to bound it) — or, for an element the run
     * owns, flushes at once and may suspend while the channel is full.
     */
    suspend fun send(element: T)


    /**
     * Sends everything [send] has buffered since the last flush as a single batch, suspending under
     * backpressure while the channel is full. A no-op when nothing is buffered.
     */
    suspend fun flush()


    /** The channel's configured elements-per-batch — the batch granularity the framework flushes a source at. */
    fun batchSize(): Int


    /** Signals this producer endpoint is finished. Idempotent. Does NOT flush (the framework flushes first). */
    fun close()
}
