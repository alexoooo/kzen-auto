package tech.kzen.auto.common.paradigm.imperative.model.control

import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


sealed class ControlTree {
    companion object {
        private val branchAttributePath = AttributePath.parse("branch")
        private val callAttributePath = AttributePath.parse("call")


        fun readSteps(
                graphStructure: GraphStructure,
                documentPath: DocumentPath
        ): BranchControlNode {
            val mainObjectLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)

            return readBranch(
                    graphStructure,
                    mainObjectLocation,
                    ScriptDocument.stepsAttributeName)
        }


        private fun readBranch(
                graphStructure: GraphStructure,
                stepLocation: ObjectLocation,
                attributeName: AttributeName
        ): BranchControlNode {
            val stepsNotation = graphStructure
                    .graphNotation
                    .transitiveAttribute(stepLocation, attributeName)
                    as? ListAttributeNotation
                    ?: return BranchControlNode(listOf())

            val objectReferenceHost = ObjectReferenceHost.ofLocation(stepLocation)
            val topLevelLocations = stepsNotation
                    .values
                    .map { ObjectReference.parse(it.asString()!!) }
                    .map { graphStructure.graphNotation.coalesce.locate(it, objectReferenceHost) }
                    .map { readStep(graphStructure, it) }

            return BranchControlNode(topLevelLocations)
        }


        private fun readStep(
                graphStructure: GraphStructure,
                stepLocation: ObjectLocation
        ): StepControlNode {
            val stepMetadata = graphStructure.graphMetadata.get(stepLocation)!!

            val stepNotation = graphStructure
                    .graphNotation
                    .documents[stepLocation.documentPath]!!
                    .objects
                    .notations[stepLocation.objectPath]!!

            val branches = mutableListOf<BranchControlNode>()

            var isCall: Boolean = false
            var callReference: DocumentPath? = null

            for (attributeMetadata in stepMetadata.attributes.values) {
                val metadataAttributes = attributeMetadata.value.attributeMetadataNotation

                val callIndicator = metadataAttributes
                        .get(callAttributePath)
                        ?.asBoolean()
                        ?: false
                if (callIndicator) {
                    isCall = true
                    callReference = stepNotation
                            .get(attributeMetadata.key)
                            ?.asString()
                            ?.let { ObjectReference.parse(it) }
                            ?.path
                }

                val branchIndex = metadataAttributes
                        .get(branchAttributePath)
                        ?.asString()
                        ?.toIntOrNull()
                if (branchIndex != null) {
                    val branchControlNode = readBranch(
                            graphStructure, stepLocation, attributeMetadata.key)

                    while (branches.size <= branchIndex) {
                        branches.add(BranchControlNode.empty)
                    }

                    branches[branchIndex] = branchControlNode
                }
            }

            return when {
                isCall -> {
                    check(branches.isEmpty())
                    CallingControlNode(stepLocation.objectPath, callReference)
                }

                branches.isNotEmpty() -> {
                    BranchingControlNode(stepLocation.objectPath, branches)
                }

                else -> {
                    SingularControlNode(stepLocation.objectPath)
                }
            }
        }
    }
}


sealed class StepControlNode: ControlTree() {
    abstract val step: ObjectPath
}


data class SingularControlNode(
        override val step: ObjectPath
): StepControlNode()


data class BranchingControlNode(
        override val step: ObjectPath,
        val branches: List<BranchControlNode>
): StepControlNode()


data class CallingControlNode(
        override val step: ObjectPath,
        val reference: DocumentPath?
): StepControlNode()


data class BranchControlNode(
        val nodes: List<StepControlNode>
): ControlTree() {
    companion object {
        val empty = BranchControlNode(listOf())
    }


    fun find(objectPath: ObjectPath): StepControlNode? {
        for (node in nodes) {
            if (node.step == objectPath) {
                return node
            }

            if (node !is BranchingControlNode) {
                continue
            }

            for (branch in node.branches) {
                val match = branch.find(objectPath)
                if (match != null) {
                    return match
                }
            }
        }

        return null
    }


    fun traverseDepthFirstNodes(visitor: (StepControlNode) -> Unit) {
        for (node in nodes) {
            visitor.invoke(node)

            if (node !is BranchingControlNode) {
                continue
            }

            for (branch in node.branches) {
                branch.traverseDepthFirstNodes(visitor)
            }
        }
    }


//    fun traverseDepthFirstLocations(visitor: (ObjectPath) -> Unit) {
//        for (node in nodes) {
//            visitor.invoke(node.step)
//
//            for (branch in node.branches) {
//                branch.traverseDepthFirstLocations(visitor)
//            }
//        }
//    }


    fun contains(target: ObjectPath): Boolean {
        for (node in nodes) {
            if (node.step == target) {
                return true
            }

            if (node !is BranchingControlNode) {
                continue
            }

            for (branch in node.branches) {
                if (branch.contains(target)) {
                    return true
                }
            }
        }

        return false
    }


    fun predecessors(
            target: ObjectPath
    ): List<ObjectPath> {
        val buffer = mutableListOf<ObjectPath>()
        predecessors(target, buffer)
        return buffer
    }


    private fun predecessors(
            target: ObjectPath,
            buffer: MutableList<ObjectPath>
    ) {
        for (node in nodes) {
            if (node.step == target) {
                break
            }

            if (node is BranchingControlNode) {
                for (branch in node.branches) {
                    if (branch.contains(target)) {
                        branch.predecessors(target, buffer)
                        return
                    }
                }
            }

            buffer.add(node.step)
        }
    }
}

