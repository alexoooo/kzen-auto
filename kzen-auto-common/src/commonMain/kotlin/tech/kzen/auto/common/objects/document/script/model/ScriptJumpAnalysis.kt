package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * Move-to (Set Next Statement) target analysis over a Script's root [ScriptTree] (execution-control phase 2).
 * A jump moves the run's pointer to [target] WITHOUT executing the intervening steps — backward = re-run from
 * [target], forward = skip over — realised as outcome-set surgery on the carried migration capture plus the
 * existing migrate rebuild (see `ScriptRunContext.restore`).
 *
 * Layered on [ScriptNestingAnalysis]: reuses its `rerun`-flag detection (a loop body is a v1-invalid target,
 * execution-control decision 3) and its ancestor-path enumeration ([ScriptNestingAnalysis.enclosingPath]); adds
 * only the jump-specific set computations. Notation-driven throughout — no hardcoded step types.
 */
object ScriptJumpAnalysis {
    /**
     * The rebuilt-run surgery for a jump to [target], all as root-document [ObjectPath]s:
     * - [ancestors]: descend set — the container steps (enclosing IfSteps) on the path root -> target, which
     *   must RE-RUN (re-evaluate their condition) but NOT park at their own boundary. Excludes the root `main`.
     * - [precedingOnPath]: skip candidates — the steps the spine visits before [target] (earlier siblings at
     *   each level up the path). Those the restore keeps no outcome for become the skip set (short-circuited
     *   value-less).
     * - [dropSet]: outcomes to discard from the carried capture — ancestors + [target] + every descendant in
     *   document order at/after [target] (its stale forward outcomes, and the body of a loop it restarts).
     *
     * When [valid] is false, [invalidReason] names why (target not a jumpable step) and the set fields are empty.
     */
    data class ScriptJumpPlan(
        val valid: Boolean,
        val invalidReason: String?,
        val ancestors: List<ObjectPath>,
        val precedingOnPath: List<ObjectPath>,
        val dropSet: Set<ObjectPath>
    ) {
        companion object {
            fun invalid(reason: String): ScriptJumpPlan =
                ScriptJumpPlan(false, reason, listOf(), listOf(), setOf())
        }
    }


    /**
     * Compute the [ScriptJumpPlan] for jumping to [target] in [scriptTree] (the root tree of [documentPath]),
     * against the current [graphNotation]. Valid iff [target] resolves to a jumpable step: it exists in the
     * tree, it lives in an executable step-list branch (not a `parameters` / `item` binding — both are
     * `is: ScriptStep` archetypes, so the hosting branch, not the inheritance chain, is what distinguishes a
     * step from a binding), and it is not inside a `rerun`-flagged loop body (a jump TO a loop step itself is
     * valid — the loop restarts at iteration 0).
     */
    fun plan(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        target: ObjectPath
    ): ScriptJumpPlan {
        val ordered = scriptTree.orderedDescendantObjectPaths()
        val targetIndex = ordered.indexOf(target)
        if (targetIndex < 0) {
            return ScriptJumpPlan.invalid("Not a step in this Script")
        }

        val path = ScriptNestingAnalysis.enclosingPath(scriptTree, target)
            ?: return ScriptJumpPlan.invalid("Not a step in this Script")

        // The branch [target] directly lives in. A binding (loop item / script parameter) is a ScriptStep
        // archetype but is not walked by the spine, so it is not a jumpable target.
        val hostingAttribute = path.last().second
        if (hostingAttribute == ScriptConventions.itemAttributeName ||
                hostingAttribute == ScriptConventions.parametersAttributeName) {
            return ScriptJumpPlan.invalid("Not a step (binding)")
        }

        if (ScriptNestingAnalysis.enclosingLoops(graphNotation, documentPath, scriptTree, target).isNotEmpty()) {
            return ScriptJumpPlan.invalid("Inside a loop body (not supported)")
        }

        val ancestors = path
            .map { it.first }
            .filterNot { it == ObjectPath.main }

        val precedingOnPath = scriptTree.predecessors(target)

        val dropSet = LinkedHashSet<ObjectPath>()
        dropSet.addAll(ancestors)
        // orderedDescendantObjectPaths is document-ordered, so the suffix from target is {target} ∪ steps-after
        // (already including nested-of-dropped — a nested step's index is > its parent's ≥ target's).
        dropSet.addAll(ordered.subList(targetIndex, ordered.size))

        return ScriptJumpPlan(true, null, ancestors, precedingOnPath, dropSet)
    }


    /**
     * Static structural validity of [target] as a move-to destination (existence + is-a-step + not a loop body;
     * NOT a reachability guarantee) — backs `Repositionable.canMoveTo` on the recompiled root Logic.
     */
    fun isValidTarget(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        target: ObjectPath
    ): Boolean {
        return plan(graphNotation, documentPath, scriptTree, target).valid
    }
}
