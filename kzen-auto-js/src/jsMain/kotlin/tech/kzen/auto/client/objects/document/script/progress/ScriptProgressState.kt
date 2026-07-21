package tech.kzen.auto.client.objects.document.script.progress

import tech.kzen.lib.common.exec.BinaryValue
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEvent
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId


data class ScriptProgressState(
    val loaded: Boolean = false,
    val logicRunExecutionId: LogicRunExecutionId? = null,
    val logicTraceSnapshot: LogicTraceSnapshot? = null,

    // The "next step to run" highlight: the viewed document's live frame position from the LogicStatus
    // poll (engine-owned, checkpoint at:) — null when the document isn't live (which clears the highlight
    // after completion).
    val nextToRun: ObjectLocation? = null,

    // Accumulated trace-event timeline for the current run, sorted by sequence, fetched incrementally
    // by ScriptProgressStore. Value-agnostic; a screenshot is an event whose value is a
    // BinaryExecutionValue (the RunStep detail film strip filters for those).
    val traceEvents: List<LogicTraceEvent> = listOf(),

    // Per-RunStep representative: the latest screenshot-bearing event anywhere in the RunStep's subtree
    // (its instructions sub-script + nested RunSteps). Keyed by the RunStep's ObjectStableId (rename-safe).
    val runStepRepresentative: Map<ObjectStableId, LogicTraceEvent> = mapOf(),

    // Per-RunStep set of owned execution ids (executionId.value): the executions this RunStep's invocation
    // spawned within the viewed run frame, plus their transitive descendants. Derived from the run's
    // execution tree (LogicTrace.lookupRunExecutions) seeded at the viewed document's resolved execution.
    // Keyed by the RunStep's ObjectStableId. This — not the sub-script document root — is how a RunStep's
    // screenshot strip is scoped, so two RunSteps invoking the same sub-script don't share each other's
    // frames.
    val runStepOwnedExecutions: Map<ObjectStableId, Set<String>> = mapOf()
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


    fun ownedExecutions(runStepStableId: ObjectStableId): Set<String> {
        return runStepOwnedExecutions[runStepStableId] ?: setOf()
    }


    // The screenshot frames this RunStep owns, grouped by sub-script execution in first-appearance order
    // (one group = one sub-script invocation = one buffer). [traceEvents] is sorted by sequence, so each
    // group is in execution order. The single definition of the strip's order: RunStepDisplay labels these
    // groups for the film strip, and pageScreenshots flattens them for the full-screen viewer's walk, so
    // the two can't drift when nested executions interleave by sequence.
    fun screenshotFramesByExecution(runStepStableId: ObjectStableId): List<List<LogicTraceEvent>> {
        val owned = ownedExecutions(runStepStableId)
        if (owned.isEmpty()) {
            return listOf()
        }

        val byExecution = LinkedHashMap<String, MutableList<LogicTraceEvent>>()
        for (frame in traceEvents) {
            if (frame.value !is BinaryValue || frame.executionId.value !in owned) {
                continue
            }
            byExecution.getOrPut(frame.executionId.value) { mutableListOf() }.add(frame)
        }
        return byExecution.values.toList()
    }
}
