package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * Move-to (Set Next Statement) structural analysis over a Script's root [ScriptTree] (execution-control phase 2).
 * A jump moves the run's pointer to [target] WITHOUT executing the intervening steps — backward = re-run from
 * [target], forward = skip over — realised as outcome-set surgery on the carried migration capture plus the
 * existing migrate rebuild (see `ScriptRunContext.restore`). Serves both roles a repositioning request defines:
 * the frame it addresses ([isValidTarget], [plan]) and a frame that merely hosts the addressed one
 * ([isDescendableCallSite], [descendAncestors]).
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
     * step from a binding — and not a `group: true` branch, whose children are structural branch groups), and
     * it is not inside a `rerun`-flagged loop body (a jump TO a loop step itself is valid — the loop restarts
     * at iteration 0).
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
        val (hostingContainer, hostingAttribute) = path.last()
        if (hostingAttribute == ScriptConventions.itemAttributeName ||
                hostingAttribute == ScriptConventions.parametersAttributeName) {
            return ScriptJumpPlan.invalid("Not a step (binding)")
        }

        // Likewise a structural GROUP child (an IfBranch): notation object, never executed, so nothing can park
        // at it. Notation-driven via the `group: true` marker — no step type named here.
        if (isGroupAttribute(graphNotation, documentPath, hostingContainer, hostingAttribute)) {
            return ScriptJumpPlan.invalid("Not a step (branch)")
        }

        if (ScriptNestingAnalysis.enclosingLoops(graphNotation, documentPath, scriptTree, target).isNotEmpty()) {
            return ScriptJumpPlan.invalid("Inside a loop body (not supported)")
        }

        // Group paths left in [dropSet] are harmless — no outcome ever exists for one — so they are not worth
        // filtering there the way [containerAncestors] filters them here.
        val ancestors = containerAncestors(graphNotation, documentPath, path)

        val precedingOnPath = scriptTree.predecessors(target)

        val dropSet = LinkedHashSet<ObjectPath>()
        dropSet.addAll(ancestors)
        // orderedDescendantObjectPaths is document-ordered, so the suffix from target is {target} ∪ steps-after
        // (already including nested-of-dropped — a nested step's index is > its parent's ≥ target's).
        dropSet.addAll(ordered.subList(targetIndex, ordered.size))

        return ScriptJumpPlan(true, null, ancestors, precedingOnPath, dropSet)
    }


    /**
     * The CONTAINER STEPS the rebuilt spine runs (re-evaluating an IfStep's condition) but must not park at, to
     * reach [element]: the ancestors on the path root -> [element], with the root `main` and the structural
     * branch GROUP nodes filtered out — neither is a step the spine can run. Null when [element] is not in
     * [scriptTree].
     *
     * Both repositioning roles need exactly this — the addressed frame around its jump target (as
     * [ScriptJumpPlan.ancestors]), a transit frame around the call-site it hosts the addressed frame from, which
     * needs none of the plan's drop / preceding sets, having nothing of its own to re-run or skip.
     */
    fun descendAncestors(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        element: ObjectPath
    ): List<ObjectPath>? {
        val path = ScriptNestingAnalysis.enclosingPath(scriptTree, element)
            ?: return null
        return containerAncestors(graphNotation, documentPath, path)
    }


    // An entry of [path] is a group node when the entry BEFORE it descends through one of its container's group
    // attributes — the only way to tell a notation branch group from a real container step on a bare path.
    private fun containerAncestors(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        path: List<Pair<ObjectPath, AttributeName>>
    ): List<ObjectPath> {
        val groupNodePaths = HashSet<ObjectPath>()
        for (i in 0 ..< path.size - 1) {
            val (containerPath, descendingAttribute) = path[i]
            if (isGroupAttribute(graphNotation, documentPath, containerPath, descendingAttribute)) {
                groupNodePaths.add(path[i + 1].first)
            }
        }

        return path
            .map { it.first }
            .filterNot { it == ObjectPath.main || it in groupNodePaths }
    }


    private fun isGroupAttribute(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        container: ObjectPath,
        attributeName: AttributeName
    ): Boolean {
        return attributeName in ScriptConventions.stepGroupAttributeNames(
            graphNotation, ObjectLocation(documentPath, container))
    }


    /**
     * Static structural validity of [target] as a move-to destination (existence + is-a-step + not a loop body;
     * NOT a reachability guarantee) — backs `Repositionable.canMoveTo` on the addressed frame's Logic.
     */
    fun isValidTarget(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        target: ObjectPath
    ): Boolean {
        return plan(graphNotation, documentPath, scriptTree, target).valid
    }


    /**
     * Static structural validity of [callSite] as an element the run walk can DESCEND THROUGH — backs
     * `Repositionable.canDescendThrough` on a transit frame's Logic, which must reach [callSite] with its own
     * boundary suppressed and then host the frame beyond it.
     *
     * One predicate serves both questions because a descent has to re-establish the walk's position at
     * [callSite] exactly as a jump re-establishes it at a target, so the same three structural facts decide it:
     * the element is in the tree, it is a real executable step (not a binding, not a branch group), and it is
     * not inside a `rerun`-flagged loop body. The loop clause is what makes them coincide rather than merely
     * resemble each other — resuming a descent into a loop-hosted invocation would need the loop to continue at
     * its current iteration instead of restarting at 0.
     */
    fun isDescendableCallSite(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        callSite: ObjectPath
    ): Boolean {
        return plan(graphNotation, documentPath, scriptTree, callSite).valid
    }
}
