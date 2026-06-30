package tech.kzen.auto.server.exec.script.step

import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


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
            // Live-edit replay (logic-spec §5): a step that completed in the pre-edit run re-adopts its outcome
            // without re-executing — no "next to run" highlight, no checkpoint boundary, no work — so resume
            // continues from the live frontier. Its Done trace is re-emitted so the client display matches.
            if (context.isReplayCompleted(step.stableId)) {
                last = TupleValue.ofMain(context.adoptCompleted(step.stableId))
                continue
            }
            context.publishNextStep(step.stableId)
            context.execution.checkpoint()
            context.markRunning(step.stableId)
            last = step.run(context)
            context.markDone(step.stableId, last.mainComponentValue())
        }
        context.publishNextStep(null)
        return last
    }


    fun nestedStableIds(): List<ObjectStableId> {
        return steps.flatMap { it.nestedStableIds() }
    }
}
