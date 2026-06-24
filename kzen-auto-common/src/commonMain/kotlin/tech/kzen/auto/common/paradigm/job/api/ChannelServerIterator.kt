package tech.kzen.auto.common.paradigm.job.api


/**
 * Suspending iterator over a duplex Job Channel's incoming requests, backing `for (served in serve) { … }`
 * on a [ChannelServer]. [hasNext] suspends for the next request (or end-of-stream); [next] returns it.
 */
interface ChannelServerIterator<out Req, in Rsp> {
    suspend operator fun hasNext(): Boolean

    operator fun next(): ServedRequest<Req, Rsp>
}
