package tech.kzen.auto.common.paradigm.job.api


/**
 * Producer endpoint of a one-way Job Channel. A Worker emits payloads via [send] (which suspends while
 * the channel buffer is full, providing backpressure) and signals it is done producing via [close].
 *
 * A Channel may have several producer endpoints (fan-in merge); consumers see end-of-stream only once
 * *all* of them have closed (close-on-last-producer), so a single producer's [close] is not premature EOF.
 */
interface ChannelOutput<in T> {
    /**
     * Suspends while the channel buffer is full (backpressure), then enqueues [payload].
     */
    suspend fun send(payload: T)


    /**
     * Signals this producer endpoint is finished. Idempotent.
     */
    fun close()
}
