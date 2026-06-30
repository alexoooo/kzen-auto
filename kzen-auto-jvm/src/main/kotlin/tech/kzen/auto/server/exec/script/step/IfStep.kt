package tech.kzen.auto.server.exec.script.step

import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Conditional branch: read the boolean a predecessor step produced and run one branch. The branch choice is
 * an ordinary `if` on the coroutine stack — no persisted `State`; a pause inside the chosen branch resumes
 * into the same branch because the suspension point is already there. The branch (a [SequenceStep]) drives
 * its own steps' trace lifecycle; this step's own value (the branch result) is recorded by the enclosing
 * sequence.
 */
class IfStep(
    override val stableId: ObjectStableId,
    private val conditionStableId: ObjectStableId,
    private val thenBranch: SequenceStep,
    private val elseBranch: SequenceStep
): ScriptStepLogic {
    override suspend fun run(context: ScriptRunContext): TupleValue {
        val condition = context.referencedValue(conditionStableId) as? Boolean
            ?: error("If condition is not a boolean: $conditionStableId")

        val branch =
            if (condition) {
                thenBranch
            }
            else {
                elseBranch
            }

        return branch.run(context)
    }


    // A branch runs at most once, so a re-entered If (paused inside its chosen branch pre-edit) keeps replay on:
    // the condition re-evaluates deterministically to the same branch and that branch's completed steps
    // short-circuit. Hence no dropReplay here (unlike a loop) — these ids are exposed only so an ENCLOSING loop
    // that re-runs drops this whole sub-tree.
    override fun nestedStableIds(): List<ObjectStableId> {
        return listOf(stableId) + thenBranch.nestedStableIds() + elseBranch.nestedStableIds()
    }
}
