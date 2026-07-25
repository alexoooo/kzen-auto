package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.persistentListOf


/**
 * Enclosing-structure analysis over a Script's [ScriptTree]: which loop steps enclose a target step. Driven
 * entirely by notation — the `rerun` attribute-metadata flag a loop declares on its body branch
 * (execution-control decision 8) — rather than by hardcoded step types, so a third-party loop step joins loop
 * semantics declaratively. [ScriptTree] already discovers every nested branch generically from object nesting,
 * so this helper needs no hardcoded branch list.
 *
 * Consumed by ControlStep validation (a Skip/Finish must target an enclosing `rerun`-flagged loop), the client
 * loop-selection dropdown (phase 5), and (execution-control phase 2) ScriptJumpAnalysis.
 */
object ScriptNestingAnalysis {
    // A loop flags the branch it re-runs as `rerun: true` under its archetype's `meta.<branch>` (ForEachStep /
    // DoWhileStep `meta.steps.rerun`). Read off a concrete loop instance through the `is:` inheritance chain.
    private const val reRunKey = "rerun"


    /**
     * The loop steps enclosing [target] in [scriptTree]'s document, INNERMOST-first: each ancestor on the path
     * root -> [target] whose hosting attribute of the descending branch is `rerun`-flagged ([isReRunAttribute]).
     * Empty when [target] is at the document root or nested only under non-`rerun` branches (an If's then/else).
     * [scriptTree] must be the root tree of [documentPath].
     */
    fun enclosingLoops(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        scriptTree: ScriptTree,
        target: ObjectPath
    ): List<ObjectLocation> {
        val path = enclosingPath(scriptTree, target)
            ?: return listOf()

        val result = ArrayList<ObjectLocation>()
        for ((ancestorPath, hostingAttribute) in path) {
            val ancestorLocation = ObjectLocation(documentPath, ancestorPath)
            if (isReRunAttribute(graphNotation, ancestorLocation, hostingAttribute)) {
                result.add(ancestorLocation)
            }
        }
        // pathTo yields outermost-first; enclosing loops are reported innermost-first.
        return result.asReversed()
    }


    /**
     * Whether [hostingAttribute] on [container]'s type is `rerun`-flagged — i.e. the branch it hosts re-runs
     * (a loop body). Reads `meta.<hostingAttribute>.rerun` through the `is:` inheritance chain, so a concrete
     * loop instance inherits the flag from its archetype (ForEachStep / DoWhileStep, or a third-party loop).
     */
    fun isReRunAttribute(
        graphNotation: GraphNotation,
        container: ObjectLocation,
        hostingAttribute: AttributeName
    ): Boolean {
        val reRunPath = AttributePath(
            NotationConventions.metaAttributeName,
            AttributeNesting(persistentListOf(
                AttributeSegment.ofKey(hostingAttribute.value),
                AttributeSegment.ofKey(reRunKey))))
        return graphNotation.firstAttribute(container, reRunPath)?.asBoolean() == true
    }


    /**
     * The executable body steps in [node]'s subtree, in document order — a strict subsequence of
     * [ScriptTree.orderedDescendantObjectPaths] with the value-binding branches (a Script's `parameters`, a
     * ForEach's `item`) and everything nested under them removed. Executable-ness is notation-driven:
     * a branch counts iff [ScriptConventions.stepBranchAttributeNames] lists it (`is: List, of: ScriptStep`
     * by exact name), so a third-party branching step is included without editing this, and a third-party
     * BINDING branch is excluded without naming it here.
     *
     * The rows a Script paints an execution margin band beside: every step the engine can stop at, and
     * nothing that merely looks like one. [node] must be the root tree of [documentPath].
     */
    fun orderedExecutableStepPaths(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        node: ScriptTree
    ): List<ObjectPath> {
        val buffer = mutableListOf<ObjectPath>()
        collectExecutable(graphNotation, documentPath, node, buffer)
        return buffer
    }


    private fun collectExecutable(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        node: ScriptTree,
        buffer: MutableList<ObjectPath>
    ) {
        // NB: also the stale-node guard — stepBranchAttributeNames answers empty for a location that left the
        //     notation (deleted / renamed mid-publish), terminating the walk instead of throwing.
        val executableBranches = ScriptConventions
            .stepBranchAttributeNames(graphNotation, ObjectLocation(documentPath, node.objectPath))
            .toHashSet()

        if (executableBranches.isEmpty()) {
            return
        }

        for ((attributeName, childTrees) in node.children) {
            if (attributeName !in executableBranches) {
                continue
            }
            for (childTree in childTrees) {
                buffer.add(childTree.objectPath)
                collectExecutable(graphNotation, documentPath, childTree, buffer)
            }
        }
    }


    /**
     * The ancestors of [target] in [node]'s subtree, OUTERMOST-first (starting at [node] itself — the root
     * `main` when called on a root [ScriptTree]), each paired with the attribute of that ancestor under which
     * the path continues toward [target]. Null when [target] is not in the subtree. The descend containers a
     * move-to jump must re-run (its enclosing IfSteps) are the non-`main` entries; the hosting attributes feed
     * [isReRunAttribute] for loop-body detection (execution-control phase 2 ScriptJumpAnalysis).
     */
    fun enclosingPath(node: ScriptTree, target: ObjectPath): List<Pair<ObjectPath, AttributeName>>? {
        for ((attributeName, childTrees) in node.children) {
            for (childTree in childTrees) {
                if (childTree.objectPath == target) {
                    return listOf(node.objectPath to attributeName)
                }
                val below = enclosingPath(childTree, target)
                if (below != null) {
                    return listOf(node.objectPath to attributeName) + below
                }
            }
        }
        return null
    }
}
