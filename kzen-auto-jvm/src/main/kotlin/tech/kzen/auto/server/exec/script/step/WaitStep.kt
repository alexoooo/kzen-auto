package tech.kzen.auto.server.exec.script.step

import kotlinx.coroutines.delay
import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/** Pause for a fixed duration via a coroutine [delay] — no engine thread is blocked while waiting. */
class WaitStep(
    override val stableId: ObjectStableId,
    private val milliseconds: Long
): ScriptStepLogic {
    override suspend fun run(context: ScriptRunContext): TupleValue {
        delay(milliseconds)
        return TupleValue.empty
    }
}
