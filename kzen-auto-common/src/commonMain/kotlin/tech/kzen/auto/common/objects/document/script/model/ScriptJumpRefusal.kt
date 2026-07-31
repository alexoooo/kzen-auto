package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.paradigm.logic.MoveToRefusal
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * Why [ScriptJumpAnalysis] refuses [target] as a move-to (Set Next Statement) destination, worded for the user.
 * Both sides ask: the server refusing a request it received, and the client refusing a drag before it sends one.
 * They reach the same error panel, so one classifier serves both — two would put two sentences on one surface.
 *
 * Classified from the structural predicates, never from [ScriptJumpAnalysis.ScriptJumpPlan.invalidReason],
 * whose wording is internal and free to change. The predicates are a complete discriminator on their own: a
 * loop-body step IS walked by the spine (which is exactly why a transit descent through one is supported) and a
 * jump is the one thing that can't land in it, so the two disagreeing means the loop case and nothing else.
 */
object ScriptJumpRefusal {
    fun reason(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        target: ObjectPath
    ): String {
        if (!ScriptJumpAnalysis.isDescendableCallSite(graphNotation, documentPath, scriptTree, target)) {
            return MoveToRefusal.targetNotJumpable()
        }

        // The OUTERMOST enclosing loop (the list runs innermost-first) is the one the advice may name: an inner
        // loop step sits in the outer loop's body and is refused in turn, so naming it would send the user
        // straight into a second refusal.
        val outermostLoop = ScriptNestingAnalysis
            .enclosingLoops(graphNotation, documentPath, scriptTree, target)
            .lastOrNull()
            ?: return MoveToRefusal.targetNotJumpable()

        return MoveToRefusal.targetInsideLoop(outermostLoop.objectPath.name.value)
    }
}
