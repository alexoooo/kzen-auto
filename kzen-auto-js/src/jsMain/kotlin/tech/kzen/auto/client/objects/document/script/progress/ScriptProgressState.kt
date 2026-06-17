package tech.kzen.auto.client.objects.document.script.progress

import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEvent
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.service.store.normal.ObjectStableId


data class ScriptProgressState(
    val loaded: Boolean = false,
    val logicRunExecutionId: LogicRunExecutionId? = null,
    val logicTraceSnapshot: LogicTraceSnapshot? = null,

    // Accumulated trace-event timeline for the current run, sorted by sequence, fetched incrementally
    // by ScriptProgressStore. Value-agnostic; a screenshot is an event whose value is a
    // BinaryExecutionValue (the RunStep detail film strip filters for those).
    val traceEvents: List<LogicTraceEvent> = listOf(),

    // Per-RunStep representative: the latest screenshot-bearing event anywhere in the RunStep's subtree
    // (its instructions sub-script + nested RunSteps). Keyed by the RunStep's ObjectStableId (rename-safe).
    val runStepRepresentative: Map<ObjectStableId, LogicTraceEvent> = mapOf()
) {
    data class MostRecentResult(
        val logicRunExecutionId: LogicRunExecutionId?
    )


    fun hasProgress(): Boolean {
        return logicRunExecutionId != null
    }


    fun representativeFrame(runStepStableId: ObjectStableId): LogicTraceEvent? {
        return runStepRepresentative[runStepStableId]
    }
}
