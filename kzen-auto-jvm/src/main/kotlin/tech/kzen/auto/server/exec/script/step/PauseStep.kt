package tech.kzen.auto.server.exec.script.step

import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Explicit breakpoint: suspend this node until the run is resumed. Re-running it (e.g. inside a loop) pauses
 * again for free — there is no `resumed` flag to reset, because the suspension is the coroutine re-reaching
 * this call, not a remembered boolean.
 */
class PauseStep(
    override val stableId: ObjectStableId
): ScriptStepLogic {
    override suspend fun run(context: ScriptRunContext): TupleValue {
        context.execution.pauseHere(PauseReason.Explicit)
        return TupleValue.empty
    }
}
