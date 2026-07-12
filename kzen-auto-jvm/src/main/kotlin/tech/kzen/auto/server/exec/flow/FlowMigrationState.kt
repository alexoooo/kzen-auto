package tech.kzen.auto.server.exec.flow

import tech.kzen.auto.common.paradigm.flow.model.exec.ActiveVertexModel
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * The durable state a Flow run carries across a live edit (logic-spec §5): captured at the quiescent migration
 * barrier (the Flow root's [tech.kzen.lib.common.exec.engine.Execution.onCapture]) and restored into the rebuilt
 * run by the root's stable identity (its [tech.kzen.lib.common.exec.engine.Execution.restored]). It is the Flow's
 * "DAG progress so far": each vertex's [ActiveVertexModel] (its accumulator state, in-flight message, remaining
 * batch, stream position and epoch) keyed by the vertex's rename-stable id, plus the output vertices' already
 * harvested values.
 *
 * The rebuilt [FlowRun] adopts this map before walking the DAG, so [FlowUtils.next][tech.kzen.auto.common.paradigm.flow.util.FlowUtils]
 * re-selects from the carried progress and the run continues from the live frontier instead of restarting.
 * Carrying the progress is what stops a non-idempotent sink from re-processing the already-consumed prefix and a
 * non-replayable source from re-emitting it after an edit.
 *
 * A vertex the edit removed simply isn't re-selected by the rebuilt DAG (the walker, snapshot and end-of-run
 * clearing all iterate the new matrix), so its carried entry is inert — the best-effort default of spec §5.
 */
data class FlowMigrationState(
    val activeVertices: Map<ObjectStableId, ActiveVertexModel>,
    val outputAccumulator: Map<TupleComponentName, Any?>
)
