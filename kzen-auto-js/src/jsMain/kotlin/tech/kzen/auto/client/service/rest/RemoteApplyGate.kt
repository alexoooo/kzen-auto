package tech.kzen.auto.client.service.rest


/**
 * Tracks the remote notation writes in flight and lets a server fetch wait for them to land.
 *
 * `MirroredGraphStore.apply` runs the local and remote applies concurrently and publishes the local commit first, so
 * a server call launched from that publish (a `File` row's format resolution, say) can be served from pre-commit
 * notation and fail against a document the server has not yet seen. Validators solve the same race with a digest
 * handshake (`ValidationDigestEcho`); a fetch whose stale answer is a plain failure has nothing to echo, so it waits
 * here instead: [ClientRestGraphStore] brackets every remote apply with [begin] / [end], and [whenSettled] runs its
 * block once no write is in flight.
 */
class RemoteApplyGate {
    private var inFlight = 0
    private val settleCallbacks = mutableListOf<() -> Unit>()


    fun begin() {
        inFlight++
    }


    fun end() {
        check(inFlight > 0) { "Remote apply ended without a matching begin" }
        inFlight--
        if (inFlight == 0) {
            drain()
        }
    }


    fun settled(): Boolean = inFlight == 0


    /** Runs [block] now when nothing is in flight, otherwise once when the last in-flight write ends. */
    fun whenSettled(block: () -> Unit) {
        if (settled()) {
            block()
        }
        else {
            settleCallbacks.add(block)
        }
    }


    // Copied then cleared before running: a callback that begins a new write must neither re-enter this drain nor
    // be re-run by it, and one queued during the drain waits for that new write.
    private fun drain() {
        val pending = settleCallbacks.toList()
        settleCallbacks.clear()
        pending.forEach { it() }
    }
}
