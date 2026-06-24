package tech.kzen.auto.common.paradigm.job.api


/**
 * Serving endpoint of a duplex Job Channel — the "actor" side. A serving Worker handles each request and
 * replies to it, looping until every client endpoint has closed:
 *
 *     for (served in serve) { served.reply(handle(served.request)) }
 *
 * Iteration (and [receive]) terminate at end-of-stream, signalled once all client endpoints have closed.
 */
interface ChannelServer<out Req, in Rsp> {
    /**
     * Suspends until the next request is available.
     *
     * @return the next request paired with its one-shot reply, or null at end-of-stream (all clients closed).
     */
    suspend fun receive(): ServedRequest<Req, Rsp>?


    /**
     * Suspending iterator backing `for (served in serve) { … }`; iterates until end-of-stream.
     */
    operator fun iterator(): ChannelServerIterator<Req, Rsp>
}
