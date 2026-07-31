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
 * Layered on [ScriptNestingAnalysis]: reuses its `rerun`-flag detection (a loop body is a v1-invalid jump
 * TARGET, execution-control decision 3 — transit THROUGH a call site inside one is supported, see
 * [isDescendableCallSite]) and its ancestor-path enumeration ([ScriptNestingAnalysis.enclosingPath]); adds only
 * the jump-specific set computations. Notation-driven throughout — no hardcoded step types.
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
     * against the current [graphNotation]. Valid iff [target] is an element the spine walks ([walkedElement])
     * AND is not inside a `rerun`-flagged loop body.
     *
     * That loop clause is the jump's alone — a DESCENT through a call site in the same body is supported, see
     * [isDescendableCallSite]. Re-pointing the walk INTO a body would have to decide which iteration the
     * target lands in and re-point the loop's own cursor at it, and no such surgery exists: a loop resumes the
     * iteration it was already on, it cannot be sent to a different one. A jump TO the loop step itself is
     * valid — its whole subtree drops, so it restarts at iteration 0.
     */
    fun plan(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        target: ObjectPath
    ): ScriptJumpPlan {
        val path = when (val walked = walkedElement(graphNotation, documentPath, scriptTree, target)) {
            is WalkedElement.NotWalked -> return ScriptJumpPlan.invalid(walked.reason)
            is WalkedElement.Walked -> walked.path
        }

        if (ScriptNestingAnalysis.enclosingLoops(graphNotation, documentPath, scriptTree, target).isNotEmpty()) {
            return ScriptJumpPlan.invalid("Inside a loop body (not supported)")
        }

        val ordered = scriptTree.orderedDescendantObjectPaths()
        // Non-negative: [walkedElement] resolved an enclosing path, so [target] is in the tree, and both
        // enumerations cover the same nodes.
        val targetIndex = ordered.indexOf(target)

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
     * The CONTAINER STEPS the rebuilt spine runs (re-evaluating an IfStep's condition, re-entering a loop at
     * its carried cursor) but must not park at, to reach [element]: the ancestors on the path root -> [element],
     * with the root `main` and the structural branch GROUP nodes filtered out — neither is a step the spine can
     * run. Null when [element] is not in [scriptTree].
     *
     * Both repositioning roles need exactly this — the addressed frame around its jump target (as
     * [ScriptJumpPlan.ancestors], where a loop ancestor cannot arise because [plan] refuses a loop-body target),
     * a transit frame around the call-site it hosts the addressed frame from, which needs none of the plan's
     * drop / preceding sets, having nothing of its own to re-run or skip.
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


    /**
     * The structural verdict both repositioning roles need of an element, and everything the transit role needs
     * ([isDescendableCallSite]): [WalkedElement.Walked] with its enclosing path when the spine walks it, else
     * [WalkedElement.NotWalked] naming why it does not.
     */
    private sealed class WalkedElement {
        class Walked(val path: List<Pair<ObjectPath, AttributeName>>): WalkedElement()
        class NotWalked(val reason: String): WalkedElement()
    }


    /**
     * Whether the spine walks [element] at all: it is in [scriptTree], and the branch DIRECTLY hosting it is an
     * executable step list. Two hosting branches are not, and both hold `is: ScriptStep` archetypes — so the
     * hosting branch, not the inheritance chain, is what tells a step from a non-step:
     *
     * - a value BINDING (a loop `item`, a Script `parameters`), which the spine never visits;
     * - a `group: true` branch, whose children are structural branch groups (an IfStep's IfBranch) — notation
     *   objects that are never executed, so nothing can park at one or descend through it. Notation-driven via
     *   the `group: true` marker; no step type is named here.
     */
    private fun walkedElement(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        element: ObjectPath
    ): WalkedElement {
        val path = ScriptNestingAnalysis.enclosingPath(scriptTree, element)
            ?: return WalkedElement.NotWalked("Not a step in this Script")

        val (hostingContainer, hostingAttribute) = path.last()
        if (hostingAttribute == ScriptConventions.itemAttributeName ||
                hostingAttribute == ScriptConventions.parametersAttributeName) {
            return WalkedElement.NotWalked("Not a step (binding)")
        }

        if (isGroupAttribute(graphNotation, documentPath, hostingContainer, hostingAttribute)) {
            return WalkedElement.NotWalked("Not a step (branch)")
        }

        return WalkedElement.Walked(path)
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
     * Static structural validity of [target] as a move-to destination (walked element + not inside a loop body;
     * NOT a reachability guarantee) — backs `Repositionable.canMoveTo` on the addressed frame's Logic. The loop
     * clause is what makes this STRICTER than [isDescendableCallSite]; [plan] says why it is the jump's alone.
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
     * A transit frame repositions NOTHING of its own, so being walked at all ([walkedElement]) is the whole
     * requirement — no drop set, no skip set, and in particular no loop clause. A call site inside a
     * `rerun`-flagged body is descendable because the walk that reaches it is the loop's ordinary mid-flight
     * resume, not a new position the analysis has to invent:
     *
     * - the loop re-records its cursor at the start of EVERY iteration (`ForEachStep`'s live iterator plus the
     *   in-flight item, `DoWhileStep`'s completed-iteration count), and `ScriptRunContext.restore` keeps the
     *   carries wholesale on the transit path, so the rebuilt loop re-enters at the iteration it was on;
     * - that resumed iteration deliberately SKIPS its `StepExecution.dropReplay` reset, so its completed body
     *   prefix — everything before [callSite] — replay-adopts instead of re-running;
     * - the descend claim is one-shot (`ScriptRunContext.descendSteps` is claimed by removal, and the engine
     *   clears the call-site hop at the hosting that consumes it), so exactly that iteration's invocation is
     *   hosted with the boundary suppressed and every later iteration takes its ordinary boundary.
     */
    fun isDescendableCallSite(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        callSite: ObjectPath
    ): Boolean {
        return walkedElement(graphNotation, documentPath, scriptTree, callSite) is WalkedElement.Walked
    }
}
