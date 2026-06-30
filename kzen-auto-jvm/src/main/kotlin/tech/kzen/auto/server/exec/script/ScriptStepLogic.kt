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

    /**
     * This step's stable id plus those of every step nested within it (an If's branches, a loop's body). A
     * re-running loop drops these from the replay set ([ScriptRunContext.dropReplay]) so its body re-executes
     * live rather than short-circuiting on a stale per-iteration outcome. The default is the leaf case (just this
     * step); container steps (If / ForEach / DoWhile) override to include their nested sequences. A [RunStep]'s
     * child is a separate engine node with its own migration, so it is NOT included.
     */
    fun nestedStableIds(): List<ObjectStableId> {
        return listOf(stableId)
    }
}
