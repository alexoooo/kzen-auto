package tech.kzen.auto.server.exec.script

import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * The durable state a Script run carries across a live edit (logic-spec §5): captured at the quiescent migration
 * barrier (the Script root's [tech.kzen.lib.common.exec.engine.Execution.onCapture]) and restored into the
 * rebuilt run by the root's stable identity (its [tech.kzen.lib.common.exec.engine.Execution.restored]). It is
 * the Script's "completed work so far": the outcome each step that finished produced (keyed by the step's
 * rename-stable id, in completion order) plus the last Result value.
 *
 * On the rebuilt run the [ScriptRunContext.runSteps] spine replays against this state:
 * a step whose id is present re-adopts its outcome WITHOUT re-executing (no checkpoint, no work), so resume
 * continues from the live frontier instead of restarting; a step absent (the next-to-run, anything the edit
 * added, or a loop body the loop re-runs — see below) executes live against the new definition.
 *
 * Unlike the old `StatefulLogicElement` migration, a loop paused mid-iteration does NOT resume mid-loop: a
 * coroutine's `for` position can't be re-pointed at the rebuilt body, so a not-yet-completed loop restarts from
 * its first iteration (its body's stale per-step outcomes are dropped on replay so values stay correct — no
 * stale carry, no double count). A *completed* loop carries its own outcome and short-circuits wholesale. This
 * is the bounded cost of the coroutine executor model; mid-loop resume parity is a tracked follow-up.
 */
data class ScriptMigrationState(
    val completedOutcomes: Map<ObjectStableId, Any?>,
    val result: TupleValue?
)
