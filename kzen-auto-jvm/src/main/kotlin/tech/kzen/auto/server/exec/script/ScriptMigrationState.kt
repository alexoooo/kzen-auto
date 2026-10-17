package tech.kzen.auto.server.exec.script

import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.engine.resource.Hold
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * The state a Script run carries across a live edit (logic-spec §5): captured at the quiescent migration
 * barrier (the Script root's [tech.kzen.lib.common.exec.engine.Execution.onCapture]) and restored into the
 * rebuilt run by the root's stable identity. Replay semantics — outcome re-adoption without re-execution,
 * whole-subtree container adoption, dropped loop iterations — live in [ScriptRunContext.runSteps] and its
 * helpers ([ScriptRunContext.adoptCompleted], [ScriptRunContext.dropReplay]).
 *
 * - [completedOutcomes]: outcome per finished step (any depth), keyed by rename-stable id, in completion order.
 * - [stepCarry]: opaque mid-flight sub-state per step, e.g. a loop's live iterator cursor recorded via
 *   [tech.kzen.auto.server.objects.script.api.StepExecution.recordCarry]. Best-effort by design: a carry
 *   derived from the old definition keeps its pre-edit course to its natural end rather than re-running
 *   completed side effects.
 * - [result]: the captured Result value.
 * - [stepHolds]: the frame's hold on each value-graph slot's resources ([tech.kzen.lib.common.exec.engine.Execution.hold]).
 *   A migration never releases one — the engine lifts the holds with the frame, so an owned step value stays
 *   open across the edit — and the rebuilt run releases a carried hold only when it re-records that slot.
 */
data class ScriptMigrationState(
    val completedOutcomes: Map<ObjectStableId, DataValue?>,
    val stepCarry: Map<ObjectStableId, Any?>,
    val result: DataBindings?,
    val stepHolds: Map<ObjectStableId, Hold>
)
