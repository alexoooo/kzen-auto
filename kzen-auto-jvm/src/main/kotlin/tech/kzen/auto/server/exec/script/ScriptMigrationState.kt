package tech.kzen.auto.server.exec.script

import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * The durable state a Script run carries across a live edit (logic-spec §5): captured at the quiescent migration
 * barrier (the Script root's [tech.kzen.lib.common.exec.engine.Execution.onCapture]) and restored into the
 * rebuilt run by the root's stable identity (its [tech.kzen.lib.common.exec.engine.Execution.restored]). It is
 * the Script's "completed work so far": the outcome each step that finished produced (keyed by the step's
 * rename-stable id, in completion order), each mid-flight step's opaque carry sub-state (see below), plus the
 * captured Result value.
 *
 * On the rebuilt run the [ScriptRunContext.runSteps] spine replays against this state:
 * a step whose id is present re-adopts its outcome WITHOUT re-executing (no checkpoint, no work), so resume
 * continues from the live frontier instead of restarting; a step absent (the next-to-run, anything the edit
 * added, or a loop body iteration-reset by [ScriptRunContext.dropReplay]) executes live against the new
 * definition.
 *
 * [stepCarry] exists because a coroutine's loop position can't be re-pointed at the rebuilt body: a
 * not-yet-completed loop necessarily re-enters its `run` from the top, so it records a cursor holding its LIVE
 * iterator (plus the in-flight item and collected outputs —
 * [tech.kzen.auto.server.objects.script.api.StepExecution.recordCarry] at each iteration's start, cleared on
 * completion) and, restored, continues the same traversal: the in-flight iteration replays its completed body
 * prefix to the frontier and the iterator supplies the rest — no side-effectful iteration re-runs, for any
 * Iterable. Any step type (including third-party) can carry mid-flight state the same way. A carry survives ANY
 * edit and is best-effort by design — the step resumes against the new definition, with added nested steps
 * running live and removed ones dropping out (the §5 element-level contract); state derived from the OLD
 * definition (a carried iterator whose items producer the edit changed) keeps its pre-edit course until its
 * natural end, which interactive development prefers over re-running completed side effects. All of this is
 * in-memory within one JVM run, like the rest of the migration state.
 *
 * CONTAINERS: a completed If / loop carries its own outcome AND its nested steps' — each step that finished
 * recorded one, at whatever depth. Adopting the container therefore adopts its whole completed subtree
 * ([ScriptRunContext.adoptCompleted]), since nothing else re-walks it. The one gap is a nested step whose
 * outcome the run itself dropped — a loop-body iteration reset by [ScriptRunContext.dropReplay] keeps only the
 * final iteration — so a later move-to jump back INTO such a body treats the value-less steps as skipped and a
 * reference to one error-parks the referencing step (the decided runtime policy —
 * [ScriptRunContext.referencedValue]). Jumping to the earliest needed step instead re-runs everything.
 */
data class ScriptMigrationState(
    val completedOutcomes: Map<ObjectStableId, Any?>,
    val stepCarry: Map<ObjectStableId, Any?>,
    val result: TupleValue?
)
