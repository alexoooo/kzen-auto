package tech.kzen.auto.client.objects.document.script.progress

import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath


/**
 * Where a step's traced list (a loop's journal) is read from, by range ([ScriptProgressStore.lookupRange]): the
 * same run and execution the viewed trace snapshot was read from ([ScriptProgressState.traceExecutionId]), so
 * the list and the step's detail beside it come from the same place. [executionId] is null when the snapshot is
 * merged across the run, and the server then reads the run's latest list at [path].
 */
data class TraceListRef(
    val runId: LogicRunId,
    val executionId: LogicExecutionId?,
    val path: LogicTracePath
)
