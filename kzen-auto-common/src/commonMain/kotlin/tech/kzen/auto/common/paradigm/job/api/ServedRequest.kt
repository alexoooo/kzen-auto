package tech.kzen.auto.common.paradigm.job.api


/**
 * A single request received by a [ChannelServer], paired with its one-shot reply. The serving Worker reads
 * [request], computes a response, and delivers it via [reply] exactly once.
 */
interface ServedRequest<out Req, in Rsp> {
    val request: Req


    /**
     * Delivers the response for this request, resuming the waiting client. One-shot.
     */
    fun reply(response: Rsp)
}
