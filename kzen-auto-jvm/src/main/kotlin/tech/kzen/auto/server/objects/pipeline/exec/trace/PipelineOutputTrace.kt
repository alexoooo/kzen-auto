package tech.kzen.auto.server.objects.pipeline.exec.trace

import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.server.objects.logic.LogicTraceHandle


class PipelineOutputTrace(
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
            PipelineConventions.outputTracePath,
            ExecutionValue.of(currentOutputCount))
    }
}