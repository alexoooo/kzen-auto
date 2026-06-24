package tech.kzen.auto.common.paradigm.job.api


/**
 * Client endpoint of a duplex Job Channel: sends a request and suspends until the serving Worker replies.
 * Correlation is per call (each request carries its own one-shot reply), so concurrent in-flight requests —
 * from this or other client endpoints — are each matched to their own response.
 *
 * A Channel may have several client endpoints (fan-in to one server); the server sees end-of-stream only
 * once *all* of them have [close]d (close-on-last-client), mirroring one-way [ChannelOutput].
 */
interface ChannelClient<in Req, out Rsp> {
    /**
     * Suspends while the request buffer is full (backpressure), enqueues [request], then suspends until the
     * server replies — returning that reply.
     */
    suspend fun request(request: Req): Rsp


    /**
     * Signals this client endpoint is finished issuing requests. Idempotent.
     */
    fun close()
}
