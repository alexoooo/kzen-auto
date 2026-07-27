package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


data class ScriptTree(
    val objectPath: ObjectPath,
    val children: Map<AttributeName, List<ScriptTree>>,
    // This node's `group: true` branches (see [ScriptConventions.stepGroupAttributeNames]): the children under
    // them are structural branch groups (an IfStep's IfBranch objects), not steps. Defaults to none so a
    // hand-built tree still compiles; every production tree comes from [read].
    val groupAttributeNames: Set<AttributeName> = setOf()
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun read(documentPath: DocumentPath, graphDefinition: GraphDefinition): ScriptTree {
            val graphNotation = graphDefinition.graphStructure.graphNotation
            val documentNotation = graphNotation.documents[documentPath]
                ?: throw IllegalStateException("Not found: $documentPath")

            val objectPaths = documentNotation.objects.notations.map.keys
            return read(objectPaths, ObjectPath.main, documentNotation, documentPath, graphNotation)
        }


        private fun read(
            objectPaths: Set<ObjectPath>,
            objectPath: ObjectPath,
            documentNotation: DocumentNotation,
            documentPath: DocumentPath,
            graphNotation: GraphNotation
        ): ScriptTree {
            val subPaths = objectPaths.filter { it.startsWith(objectPath) && it != objectPath }

            val buffer = mutableMapOf<AttributeName, MutableSet<ObjectPath>>()
            for (subPath in subPaths) {
                val nextSegment = subPath.nesting.segments[objectPath.nesting.segments.size]
                val attributeName = nextSegment.attributePath.attribute
                val branchBuffer = buffer.getOrPut(attributeName) { mutableSetOf() }
                branchBuffer.add(subPath)
            }

            val builder = mutableMapOf<AttributeName, List<ScriptTree>>()
            for ((attributeName, attributePaths) in buffer) {
                val directAttributePaths = attributePaths
                    .filter { it.nesting.segments.size == objectPath.nesting.segments.size + 1 }

                val attributeTrees: List<ScriptTree> = directAttributePaths
                    .map { read(attributePaths, it, documentNotation, documentPath, graphNotation) }

                // Step order within a branch is the document position of the step objects.
                val sortedTrees = attributeTrees
                    .sortedBy { documentNotation.indexOf(it.objectPath).value }

                builder[attributeName] = sortedTrees
            }

            val groupAttributeNames = ScriptConventions
                .stepGroupAttributeNames(graphNotation, ObjectLocation(documentPath, objectPath))
                .toSet()

            return ScriptTree(objectPath, builder, groupAttributeNames)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // All descendant step paths in document order (depth-first; each branch's child list is already
    // document-sorted by read). Excludes this node itself — called on the root it yields every step,
    // skipping the root main object which is the script document, not a step.
    fun orderedDescendantObjectPaths(): List<ObjectPath> {
        val buffer = mutableListOf<ObjectPath>()
        collectDescendants(buffer)
        return buffer
    }


    private fun collectDescendants(buffer: MutableList<ObjectPath>) {
        for (childTrees in children.values) {
            for (childTree in childTrees) {
                buffer.add(childTree.objectPath)
                childTree.collectDescendants(buffer)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Value bindings in scope for the step at `target` (must be called on the root tree): every Script
    // parameter (root `parameters` branch, script-wide), plus the `item` of each enclosing ForEachStep
    // on the path to `target`. These are named typed values usable by `target` without being predecessors
    // in the body — the rowless counterpart to ScriptTree.predecessors.
    fun inScopeBindingPaths(target: ObjectPath): List<ObjectPath> {
        val buffer = mutableListOf<ObjectPath>()

        children[ScriptConventions.parametersAttributeName]
            ?.forEach { buffer.add(it.objectPath) }

        collectEnclosingItems(target, buffer)

        return buffer
    }


    // Adds the `item` binding of every node that (transitively) contains `target`. When a child subtree
    // contains the target, this node encloses it, so this node's item branch (if any) is in scope.
    private fun collectEnclosingItems(target: ObjectPath, buffer: MutableList<ObjectPath>): Boolean {
        if (objectPath == target) {
            return true
        }

        for (childTrees in children.values) {
            for (childTree in childTrees) {
                if (childTree.collectEnclosingItems(target, buffer)) {
                    children[ScriptConventions.itemAttributeName]
                        ?.forEach { buffer.add(it.objectPath) }
                    return true
                }
            }
        }

        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Everything the step at `target` may reference: the prior steps of each enclosing block plus the in-scope
    // value bindings (Script parameters / enclosing ForEach items). One name for the candidate set shared by the
    // reference selects, the expression editor, the rename rewriter and the server's expression scoping — so
    // client and server can't drift apart on what "in scope" means.
    fun inScopeReferencePaths(target: ObjectPath): List<ObjectPath> {
        return predecessors(target) + inScopeBindingPaths(target)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun predecessors(target: ObjectPath): List<ObjectPath> {
        val buffer = ArrayDeque<ObjectPath>()
        predecessors(target, buffer)
        return buffer
    }


    private fun predecessors(target: ObjectPath, buffer: ArrayDeque<ObjectPath>): Boolean {
        if (objectPath == target) {
            return true
        }

        for ((attributeName, childTrees) in children) {
            // Siblings under a GROUP branch are alternatives, not predecessors: when a later If branch runs, the
            // earlier ones did not, so neither they nor their steps are in scope. Skipping them here is the one
            // fix that gives every consumer of the scope set the same answer — the branch condition's step
            // select, in-branch step scoping, the server's expression scoping, rename rewriting and the jump
            // analysis's preceding-on-path.
            val isGroup = attributeName in groupAttributeNames

            for ((index, childTree) in childTrees.withIndex()) {
                val foundInChild = childTree.predecessors(target, buffer)
                if (foundInChild) {
                    if (! isGroup) {
                        if (childTree.children.isEmpty()) {
                            for (i in 0 ..< index) {
                                buffer.add(childTrees[i].objectPath)
                            }
                        }
                        else {
                            for (i in 0 ..< index) {
                                buffer.addFirst(childTrees[i].objectPath)
                            }
                        }
                    }
                    return true
                }
            }
        }

        return false
    }
}