package tech.kzen.auto.server.objects.report.exec.trace

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle


class ReportOutputTrace(
    private val logicTraceHandle: LogicTraceHandle
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Minimum spacing between progress publishes — the engine trace bridge (ExecutionLogicTraceHandle) is
        // push-only, so the count is published as it grows rather than lazily on query; throttle so a
        // per-batch output stage doesn't flood the trace history (mirrors the Job worker-progress throttle).
        private const val publishThrottleNanos = 200_000_000L  // 200 ms
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        // The old (LogicTraceStore-backed) handle republishes on query; the engine adapter's register is a
        // no-op (push-only), so [nextOutput] also pushes below. Harmless on the old path (an extra set).
        logicTraceHandle.register {
            publishUpdate()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Single-writer: nextOutput is driven by one output-stage consumer thread.
    @Volatile
    private var currentOutputCount = 0L

    private var lastPublishNanos = 0L


    //-----------------------------------------------------------------------------------------------------------------
    fun nextOutput(nextOutputRecords: Long) {
        currentOutputCount += nextOutputRecords

        val now = System.nanoTime()
        if (lastPublishNanos != 0L && now - lastPublishNanos < publishThrottleNanos) {
            return
        }
        lastPublishNanos = now
        publishUpdate()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun publishUpdate() {
        logicTraceHandle.set(
            ReportConventions.outputTracePath,
            ExecutionValue.of(currentOutputCount))
    }
}