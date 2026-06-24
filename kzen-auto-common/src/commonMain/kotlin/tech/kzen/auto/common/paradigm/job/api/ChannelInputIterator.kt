package tech.kzen.auto.common.paradigm.job.api


/**
 * Suspending iterator over a [ChannelInput], mirroring kotlinx's ChannelIterator so a Worker can write
 * `for (x in input) { … }`. [hasNext] suspends until either the next payload is ready or end-of-stream.
 */
interface ChannelInputIterator<out T> {
    suspend operator fun hasNext(): Boolean

    operator fun next(): T
}
