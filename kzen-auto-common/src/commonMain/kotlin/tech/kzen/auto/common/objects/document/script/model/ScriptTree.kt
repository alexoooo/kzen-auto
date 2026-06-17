package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation


data class ScriptTree(
    val objectPath: ObjectPath,
    val children: Map<AttributeName, List<ScriptTree>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun read(documentPath: DocumentPath, graphDefinition: GraphDefinition): ScriptTree {
            val documentNotation = graphDefinition.graphStructure.graphNotation.documents[documentPath]
                ?: throw IllegalStateException("Not found: $documentPath")

            val objectPaths = documentNotation.objects.notations.map.keys
            return read(objectPaths, ObjectPath.main, documentNotation)
        }


        private fun read(
            objectPaths: Set<ObjectPath>,
            objectPath: ObjectPath,
            documentNotation: DocumentNotation
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
                    .map { read(attributePaths, it, documentNotation) }

                // Step order within a branch is the document position of the step objects.
                val sortedTrees = attributeTrees
                    .sortedBy { documentNotation.indexOf(it.objectPath).value }

                builder[attributeName] = sortedTrees
            }

            return ScriptTree(objectPath, builder)
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
    fun predecessors(target: ObjectPath): List<ObjectPath> {
        val buffer = ArrayDeque<ObjectPath>()
        predecessors(target, buffer)
        return buffer
    }


    private fun predecessors(target: ObjectPath, buffer: ArrayDeque<ObjectPath>): Boolean {
        if (objectPath == target) {
            return true
        }

        for (childTrees in children.values) {
            for ((index, childTree) in childTrees.withIndex()) {
                val foundInChild = childTree.predecessors(target, buffer)
                if (foundInChild) {
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
                    return true
                }
            }
        }

        return false
    }
}