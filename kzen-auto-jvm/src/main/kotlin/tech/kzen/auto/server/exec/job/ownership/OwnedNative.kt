package tech.kzen.auto.server.exec.job.ownership

import kotlinx.coroutines.CompletableDeferred
import tech.kzen.auto.common.paradigm.job.control.ValueLease
import java.util.concurrent.atomic.AtomicBoolean


/**
 * One adopted native identity inside a run: its named leases and the single close. The count of leases is
 * the number of live holds across holders; the last release closes the native exactly once, and a hold taken
 * while another is still held ([lease] before the earlier lease's release) keeps the count from touching
 * zero between holders. Closing is a claim: whichever release (or teardown) wins closes, every later attempt
 * is a no-op, and a `close()` that throws still leaves the entry closed — the failure is thrown from the
 * release that closed (a mid-run last holder reports it) or returned from [forceClose] (teardown aggregates).
 * Thread-safe: leases are taken and released from any thread. A lending source suspends on [awaitClosed] until
 * the last release closes the native (docs/plans/2026-09-16_borrowed-elements.md §3.2).
 */
class OwnedNative internal constructor(
    val native: AutoCloseable,
    private val onClosed: (OwnedNative) -> Unit
) {
    private val lock = Any()
    private val holds = mutableMapOf<LeaseHolder, Int>()
    private val closeClaimed = AtomicBoolean(false)
    @Volatile private var total = 0
    @Volatile private var closedFlag = false
    // Created by the first waiter, completed by the close; guarded by [lock] against the close racing the wait
    private var closedSignal: CompletableDeferred<Unit>? = null


    val isClosed: Boolean
        get() = closedFlag


    /** Live holds by holder name (a snapshot). */
    fun holds(): Map<LeaseHolder, Int> = synchronized(lock) { holds.toMap() }


    fun leaseCount(): Int = total


    /** Suspends until the native is closed (the last release, or teardown); returns at once when it already is. */
    suspend fun awaitClosed() {
        val signal = synchronized(lock) {
            if (closedFlag) {
                return
            }
            closedSignal ?: CompletableDeferred<Unit>().also { closedSignal = it }
        }
        signal.await()
    }


    /** Takes one hold for [holder]; fails by name once the native is closed. */
    fun lease(holder: LeaseHolder): ValueLease {
        synchronized(lock) {
            check(!closedFlag) {
                "Native ${native.javaClass.name} is already closed; no lease can be taken"
            }
            holds[holder] = (holds[holder] ?: 0) + 1
            total++
        }
        return Lease(holder)
    }


    /** Releases every outstanding hold and closes (teardown); returns the close failure, if any. */
    fun forceClose(): Throwable? {
        synchronized(lock) {
            holds.clear()
            total = 0
        }
        return closeOnce()
    }


    private fun releaseHold(holder: LeaseHolder) {
        val last: Boolean
        synchronized(lock) {
            val current = holds[holder] ?: return
            if (current == 1) holds.remove(holder) else holds[holder] = current - 1
            total--
            last = total == 0 && !closedFlag
        }
        if (last) {
            closeOnce()?.let { throw it }
        }
    }


    private fun closeOnce(): Throwable? {
        if (!closeClaimed.compareAndSet(false, true)) {
            return null
        }
        closedFlag = true
        synchronized(lock) { closedSignal }?.complete(Unit)
        val failure = try {
            native.close()
            null
        }
        catch (e: Throwable) {
            e
        }
        onClosed(this)
        return failure
    }


    private inner class Lease(
        private val holder: LeaseHolder
    ): ValueLease {
        private val released = AtomicBoolean(false)

        override val isActive: Boolean
            get() = !released.get()

        override fun release() {
            if (released.compareAndSet(false, true)) {
                releaseHold(holder)
            }
        }
    }


    override fun toString(): String =
        "OwnedNative(${native.javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(native))}, holds=${holds()}, closed=$closedFlag)"
}
