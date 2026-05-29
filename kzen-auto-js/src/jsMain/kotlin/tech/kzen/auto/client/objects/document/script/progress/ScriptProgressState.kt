package tech.kzen.auto.client.objects.document.script.progress

import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot


data class ScriptProgressState(
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