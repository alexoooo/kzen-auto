package tech.kzen.auto.server.exec.script

import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * One step of a Script, expressed as coroutine code against the new engine surface. Position lives on the
 * coroutine stack, so there is no re-entrant continueOrStart, no StatefulLogicElement, and no hand-rolled
 * pause-state (iterator / branch choice / pausedExecution) — a control step is an ordinary `for` / `if`,
 * a boundary is [ScriptRunContext.checkpoint], and a nested run is [ScriptRunContext.host].
 *
 * Each step carries its [stableId] (the rename-stable identity of its notation object) so the enclosing
 * [SequenceStep] can drive the uniform per-step trace lifecycle — "next to run" highlight, then Running,
 * then Done with the produced value — exactly as the former MultiStep did, without each step repeating it.
 */
interface ScriptStepLogic {
    val stableId: ObjectStableId

    suspend fun run(context: ScriptRunContext): TupleValue
}
