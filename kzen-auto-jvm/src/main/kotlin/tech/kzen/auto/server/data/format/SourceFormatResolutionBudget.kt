package tech.kzen.auto.server.data.format

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import tech.kzen.auto.common.data.format.FormatResolutionBudget
import tech.kzen.auto.common.data.format.FormatResolutionPermit
import java.util.concurrent.atomic.AtomicBoolean


class SourceFormatResolutionBudget(
    private val policy: SourceFormatResolutionPolicy = SourceFormatResolutionPolicy.default
): FormatResolutionBudget, AutoCloseable {
    private val coldPermits = Semaphore(policy.maximumConcurrentColdParts)
    private val stateLock = Any()
    private val operationStarted = AtomicBoolean()

    private var closed = false
    private var coldPartsStarted = 0
    private var decodedBytes = 0L
    private var completedParts = 0
    private var activeColdParts = 0
    private var peakActiveColdParts = 0


    suspend fun <T> withinDeadline(block: suspend SourceFormatResolutionBudget.() -> T): T {
        check(operationStarted.compareAndSet(false, true)) {
            "A source-resolution budget may own only one operation"
        }
        try {
            val completed = withTimeoutOrNull(policy.overallTimeoutMillis) {
                Completed(block())
            }
            if (completed == null) {
                throw limitFailure("wall-time limit of ${policy.overallTimeoutMillis} ms")
            }
            return completed.value
        }
        finally {
            close()
        }
    }


    override suspend fun acquireColdPart(): FormatResolutionPermit {
        synchronized(stateLock) {
            check(!closed) { "Source-resolution budget is closed" }
            if (coldPartsStarted >= policy.maximumColdParts) {
                throw limitFailureLocked("cold-part limit of ${policy.maximumColdParts}")
            }
            coldPartsStarted += 1
        }

        var concurrentPermitAcquired = false
        try {
            coldPermits.acquire()
            concurrentPermitAcquired = true
            synchronized(stateLock) {
                check(!closed) { "Source-resolution budget is closed" }
                activeColdParts += 1
                peakActiveColdParts = maxOf(peakActiveColdParts, activeColdParts)
            }
        }
        catch (failure: Throwable) {
            synchronized(stateLock) {
                coldPartsStarted -= 1
            }
            if (concurrentPermitAcquired) {
                coldPermits.release()
            }
            throw failure
        }

        return ColdPartPermit()
    }


    override fun chargeDecodedBytes(count: Int) {
        require(count >= 0) { "Decoded byte count must not be negative" }
        synchronized(stateLock) {
            check(!closed) { "Source-resolution budget is closed" }
            if (count.toLong() > policy.maximumDecodedBytes - decodedBytes) {
                throw limitFailureLocked("decoded-sample limit of ${policy.maximumDecodedBytes} bytes")
            }
            decodedBytes += count
        }
    }


    internal fun snapshot(): Snapshot = synchronized(stateLock) {
        Snapshot(
            coldPartsStarted,
            decodedBytes,
            completedParts,
            activeColdParts,
            peakActiveColdParts)
    }


    override fun close() {
        synchronized(stateLock) {
            closed = true
        }
    }


    private fun limitFailure(limit: String): IllegalStateException = synchronized(stateLock) {
        limitFailureLocked(limit)
    }


    private fun limitFailureLocked(limit: String): IllegalStateException {
        return IllegalStateException(
            "Automatic format resolution stopped after $completedParts completed file(s): " +
                "the $limit was exceeded. Narrow the file filter, choose a concrete source-level format, " +
                "or raise the source-resolution policy.")
    }


    private inner class ColdPartPermit: FormatResolutionPermit {
        private val completed = AtomicBoolean()


        override fun completeSuccess() {
            finish(true)
        }


        override fun close() {
            finish(false)
        }


        private fun finish(successful: Boolean) {
            if (!completed.compareAndSet(false, true)) return
            synchronized(stateLock) {
                check(activeColdParts > 0) { "No cold source resolution is active" }
                if (successful) {
                    completedParts += 1
                }
                activeColdParts -= 1
            }
            coldPermits.release()
        }
    }


    internal data class Snapshot(
        val coldPartsStarted: Int,
        val decodedBytes: Long,
        val completedParts: Int,
        val activeColdParts: Int,
        val peakActiveColdParts: Int
    )


    private data class Completed<T>(
        val value: T
    )
}
