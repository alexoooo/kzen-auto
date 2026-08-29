package tech.kzen.auto.server.exec.script

import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.value.DataValue
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
 */
data class ScriptMigrationState(
    val completedOutcomes: Map<ObjectStableId, DataValue?>,
    val stepCarry: Map<ObjectStableId, Any?>,
    val result: DataBindings?
)
