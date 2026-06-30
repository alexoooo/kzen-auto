package tech.kzen.auto.server.exec.script.step

import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.tuple.TupleValue


/**
 * The sequential spine (the former MultiStep): run each child in document order, with a [checkpoint] boundary
 * *before* each — so a pause settles between steps and a single step advances exactly one. It also owns the
 * uniform per-step trace lifecycle for every child: publish the "next to run" highlight, settle the boundary,
 * then mark the step Running and (after it returns) Done with its produced value. The sequence has no value of
 * its own; it yields its last child's value (used as a branch / loop-body result).
 *
 * Not a [ScriptStepLogic] itself: a sequence is always a field (root spine, branch, loop body), never an entry
 * in another sequence — control steps reference it by type — so it needs no stable id of its own.
 */
class SequenceStep(
    private val steps: List<ScriptStepLogic>
) {
    suspend fun run(context: ScriptRunContext): TupleValue {
        var last = TupleValue.empty
        for (step in steps) {
            context.publishNextStep(step.stableId)
            context.execution.checkpoint()
            context.markRunning(step.stableId)
            last = step.run(context)
            context.markDone(step.stableId, last.mainComponentValue())
        }
        context.publishNextStep(null)
        return last
    }
}
