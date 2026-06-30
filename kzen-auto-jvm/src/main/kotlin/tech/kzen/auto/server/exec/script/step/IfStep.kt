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
}
