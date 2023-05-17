package tech.kzen.auto.client.objects.document.sequence.progress

import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot


data class SequenceProgressState(
    val loaded: Boolean = false,
    val logicRunExecutionId: LogicRunExecutionId? = null,
    val logicTraceSnapshot: LogicTraceSnapshot? = null
) {
    data class MostRecentResult(
        val logicRunExecutionId: LogicRunExecutionId?
    )

    fun hasProgress(): Boolean {
        return logicRunExecutionId != null
    }
}