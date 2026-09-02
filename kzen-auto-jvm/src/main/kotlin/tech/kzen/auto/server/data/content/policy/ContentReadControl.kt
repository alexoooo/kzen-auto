package tech.kzen.auto.server.data.content.policy

import java.util.concurrent.CancellationException
import kotlin.time.TimeMark
import kotlin.time.TimeSource


class ContentReadControl(
    private val policy: ContentReadPolicy
) {
    private var operationDepth = 0
    private var operationStarted: TimeMark? = null
    val maximumExpandedBytes: Long get() = policy.maximumExpandedBytes
    val inspectionRecordLimit: Long get() = policy.inspectionRecordLimit
    var expandedBytesRead: Long = 0
        private set


    fun beginOperation() {
        if (operationStarted == null) {
            operationStarted = TimeSource.Monotonic.markNow()
        }
        operationDepth++
    }


    fun endOperation() {
        check(operationDepth > 0) { "No content operation is active" }
        operationDepth--
    }


    fun checkpoint() {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Content read interrupted")
        }
        if (operationStarted?.elapsedNow()?.let { it >= policy.timeout } == true) {
            throw ContentTimeoutException(policy.timeout)
        }
    }


    fun recordExpandedBytes(count: Int) {
        require(count >= 0) { "Expanded byte count must not be negative" }
        expandedBytesRead += count
    }
}
