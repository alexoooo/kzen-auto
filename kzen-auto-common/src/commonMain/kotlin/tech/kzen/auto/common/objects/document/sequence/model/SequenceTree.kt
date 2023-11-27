package tech.kzen.auto.common.objects.document.sequence.model

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation


data class SequenceTree(
    val objectPath: ObjectPath,
    val children: Map<AttributeName, List<SequenceTree>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun read(documentNotation: DocumentNotation): SequenceTree {
            val objectPaths = documentNotation.objects.notations.values.keys
            return read(objectPaths, ObjectPath.main)
        }

        private fun read(objectPaths: Set<ObjectPath>, objectPath: ObjectPath): SequenceTree {
            val subPaths = objectPaths.filter { it.startsWith(objectPath) && it != objectPath }

            val buffer = mutableMapOf<AttributeName, MutableSet<ObjectPath>>()
            for (subPath in subPaths) {
                val nextSegment = subPath.nesting.segments[objectPath.nesting.segments.size]
                val attributeName = nextSegment.attributePath.attribute
                val branchBuffer = buffer.getOrPut(attributeName) { mutableSetOf() }
                branchBuffer.add(subPath)
            }

            val builder = mutableMapOf<AttributeName, List<SequenceTree>>()
            for ((attributeName, attributePaths) in buffer) {
                val directAttributePaths = attributePaths
                    .filter { it.nesting.segments.size == objectPath.nesting.segments.size + 1 }

                builder[attributeName] = directAttributePaths.map { read(attributePaths, it) }
            }

            return SequenceTree(objectPath, builder)
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