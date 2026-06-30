package tech.kzen.auto.server.exec.script.step

import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Invoke a nested Logic as a confined child node ([Execution.host][tech.kzen.lib.common.exec.engine.Execution.host]).
 * The engine owns the child's stepping, so step-over / step-out cross the boundary with no per-step mirroring,
 * and there is no `pausedExecution` to keep alive — the suspension is held on the host call's coroutine frame.
 * The child result is recorded + traced for this step by the enclosing [SequenceStep].
 */
class RunStep(
    override val stableId: ObjectStableId,
    private val childStableId: ObjectStableId,
    private val child: Logic,
    private val arguments: (ScriptRunContext) -> TupleValue = { TupleValue.empty }
): ScriptStepLogic {
    override suspend fun run(context: ScriptRunContext): TupleValue {
        return context.execution.host(childStableId, child, arguments(context))
    }
}
