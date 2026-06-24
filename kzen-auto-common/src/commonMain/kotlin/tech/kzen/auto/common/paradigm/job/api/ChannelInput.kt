package tech.kzen.auto.common.paradigm.job.api


/**
 * Consumer endpoint of a one-way Job Channel. A Worker reads its input stream either by suspending on
 * [receive] until the next payload arrives, or by iterating `for (x in input) { … }`. Both terminate at
 * end-of-stream — signalled once all producer endpoints have closed (close-on-last-producer).
 */
interface ChannelInput<out T> {
    /**
     * Suspends until the next payload is available.
     *
     * @return the next payload, or null at end-of-stream (the channel is closed and drained).
     */
    suspend fun receive(): T?


    /**
     * Suspending iterator backing `for (x in input) { … }`; iterates until end-of-stream.
     */
    operator fun iterator(): ChannelInputIterator<T>
}
