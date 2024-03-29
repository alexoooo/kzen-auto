package tech.kzen.auto.common.objects.document.sequence.model

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocator
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.*


data class SequenceTree(
    val objectPath: ObjectPath,
    val children: Map<AttributeName, List<SequenceTree>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun read(documentPath: DocumentPath, graphDefinition: GraphDefinition): SequenceTree {
            val documentNotation = graphDefinition.graphStructure.graphNotation.documents[documentPath]
                ?: throw IllegalStateException("Not found: $documentPath")

            val objectPaths = documentNotation.objects.notations.map.keys
            return read(objectPaths, ObjectPath.main, documentPath, documentNotation, graphDefinition)
        }


        private fun read(
            objectPaths: Set<ObjectPath>,
            objectPath: ObjectPath,
            documentPath: DocumentPath,
            documentNotation: DocumentNotation,
            graphDefinition: GraphDefinition
        ): SequenceTree {
            val objectLocation = documentPath.toObjectLocation(objectPath)
            val objectReferenceHost = ObjectReferenceHost.ofLocation(objectLocation)

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

                val attributeTrees: List<SequenceTree> = directAttributePaths
                    .map { read(attributePaths, it, documentPath, documentNotation, graphDefinition) }

                val attributeNotation = graphDefinition
                    .graphStructure
                    .graphNotation
                    .firstAttribute(objectLocation, attributeName)

                // used for ordering the steps within a branch
                val attributeDefinitionOrNull = graphDefinition[objectLocation]
                    ?.attributeDefinitions
                    ?.get(attributeName)

                val pathToIndex: Map<ObjectPath, Int> =
                    attributeTrees.associate {
                        it.objectPath to indexOf(
                            documentPath.toObjectLocation(it.objectPath),
                            attributeNotation,
                            attributeDefinitionOrNull,
                            objectReferenceHost,
                            graphDefinition.graphStructure.graphNotation.coalesce)
                    }

                val sortedTrees = attributeTrees.sortedBy { pathToIndex[it.objectPath] ?: -2 }

                builder[attributeName] = sortedTrees
            }

            return SequenceTree(objectPath, builder)
        }


        private fun indexOf(
            objectLocation: ObjectLocation,
            attributeNotation: AttributeNotation,
            attributeDefinitionOrNull: AttributeDefinition?,
            objectReferenceHost: ObjectReferenceHost,
            objectLocator: ObjectLocator
        ): Int {
            return when {
                attributeDefinitionOrNull != null ->
                    indexOfByDefinition(objectLocation, attributeDefinitionOrNull, objectReferenceHost, objectLocator)

                else ->
                    indexOfByNotation(objectLocation, attributeNotation, objectReferenceHost, objectLocator)
            }
        }


        private fun indexOfByNotation(
            objectLocation: ObjectLocation,
            attributeNotation: AttributeNotation,
            objectReferenceHost: ObjectReferenceHost,
            objectLocator: ObjectLocator
        ): Int {
            return when (attributeNotation) {
                is ScalarAttributeNotation -> {
                    val objectReference = ObjectReference.tryParse(attributeNotation.value)
                        ?: return -1
                    val referenceLocation = objectLocator.locateOptional(objectReference, objectReferenceHost)
                        ?: return -1
                    if (referenceLocation != objectLocation) {
                        return -1
                    }
                    0
                }

                is ListAttributeNotation -> {
                    for ((index, itemNotation) in attributeNotation.values.withIndex()) {
                        val itemIndex = indexOfByNotation(
                            objectLocation, itemNotation, objectReferenceHost, objectLocator)
                        if (itemIndex != -1) {
                            return index + itemIndex
                        }
                    }
                    -1
                }

                is MapAttributeNotation -> {
                    for ((index, entry) in attributeNotation.map.entries.withIndex()) {
                        val itemIndex = indexOfByNotation(
                            objectLocation, entry.value, objectReferenceHost, objectLocator)
                        if (itemIndex != -1) {
                            return index + itemIndex
                        }
                    }
                    -1
                }
            }
        }


        private fun indexOfByDefinition(
            objectLocation: ObjectLocation,
            attributeDefinition: AttributeDefinition,
            objectReferenceHost: ObjectReferenceHost,
            objectLocator: ObjectLocator
        ): Int {
            return when (attributeDefinition) {
                is ReferenceAttributeDefinition -> {
                    val objectReference = attributeDefinition.objectReference
                        ?: return -1
                    val referenceLocation = objectLocator.locateOptional(objectReference, objectReferenceHost)
                        ?: return -1
                    if (referenceLocation != objectLocation) {
                        return -1
                    }
                    0
                }

                is ListAttributeDefinition -> {
                    for (index in attributeDefinition.values.indices) {
                        val itemDefinition = attributeDefinition.values[index]
                        val itemIndex = indexOfByDefinition(
                            objectLocation, itemDefinition, objectReferenceHost, objectLocator)
                        if (itemIndex != -1) {
                            return index + itemIndex
                        }
                    }
                    -1
                }

                is MapAttributeDefinition -> {
                    for ((index, entry) in attributeDefinition.map.entries.withIndex()) {
                        val itemIndex = indexOfByDefinition(
                            objectLocation, entry.value, objectReferenceHost, objectLocator)
                        if (itemIndex != -1) {
                            return index + itemIndex
                        }
                    }
                    -1
                }

                else ->
                    -1
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