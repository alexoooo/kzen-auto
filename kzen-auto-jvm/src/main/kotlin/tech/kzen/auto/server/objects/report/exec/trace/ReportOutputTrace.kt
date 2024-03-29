package tech.kzen.auto.server.objects.report.exec.trace

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.lib.common.exec.ExecutionValue


class ReportOutputTrace(
    private val logicTraceHandle: LogicTraceHandle
) {
    //-----------------------------------------------------------------------------------------------------------------
    init {
        logicTraceHandle.register {
            publishUpdate()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Volatile
    private var currentOutputCount = 0L


    //-----------------------------------------------------------------------------------------------------------------
    fun nextOutput(nextOutputRecords: Long) {
        currentOutputCount += nextOutputRecords
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun publishUpdate() {
        logicTraceHandle.set(
            ReportConventions.outputTracePath,
            ExecutionValue.of(currentOutputCount))
    }
}