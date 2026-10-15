package tech.kzen.auto.client.service.rest

import kotlinx.coroutines.CompletableDeferred


/**
 * Orders every server request after the notation writes already sent: a request waits until none is in flight.
 *
 * `MirroredGraphStore.apply` runs the local and remote applies concurrently and publishes the local commit first, so
 * anything that reacts to an edit (a validation, a `File` row's resolution) asks the server before the server has
 * seen that edit. [ClientRestApi] brackets each notation write with [begin] / [end] and has every other request
 * [awaitSettled] first, so the server always answers from notation that includes every edit the browser has made.
 * Writes never wait: a write waiting for itself would never start.
 */
internal class NotationWriteBarrier {
    private var inFlight = 0
    private var settled: CompletableDeferred<Unit>? = null


    fun begin() {
        inFlight++
    }


    fun end() {
        check(inFlight > 0) { "Notation write ended without a matching begin" }
        inFlight--
        if (inFlight == 0) {
            val waiting = settled
            settled = null
            waiting?.complete(Unit)
        }
    }


    fun settled(): Boolean = inFlight == 0


    /** Returns at once when no write is in flight, otherwise once the last in-flight write ends. */
    suspend fun awaitSettled() {
        while (inFlight > 0) {
            val waiting = settled ?: CompletableDeferred<Unit>().also { settled = it }
            waiting.await()
        }
    }
}
