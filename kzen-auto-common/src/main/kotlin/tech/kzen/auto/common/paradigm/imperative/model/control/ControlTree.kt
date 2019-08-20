package tech.kzen.auto.common.paradigm.imperative.model.control

import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation


sealed class ControlTree {
    companion object {
        private val branchAttributePath = AttributePath.parse("branch")


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

            val topLevelLocations = stepsNotation
                    .values
                    .map { ObjectReference.parse(it.asString()!!) }
                    .map { graphStructure.graphNotation.coalesce.locate(it) }
                    .map { readStep(graphStructure, it) }

            return BranchControlNode(topLevelLocations)
        }


        private fun readStep(
                graphStructure: GraphStructure,
                stepLocation: ObjectLocation
        ): StepControlNode {
            val stepMetadata = graphStructure.graphMetadata.get(stepLocation)!!

            val branches = mutableListOf<BranchControlNode>()

            for (attributeMetadata in stepMetadata.attributes.values) {
                val branchIndex = attributeMetadata
                        .value
                        .attributeMetadataNotation
                        .get(branchAttributePath)
                        ?.asString()
                        ?.toIntOrNull()
                        ?: continue

                val branchControlNode = readBranch(
                        graphStructure, stepLocation, attributeMetadata.key)

                while (branches.size <= branchIndex) {
                    branches.add(BranchControlNode.empty)
                }

                branches[branchIndex] = branchControlNode
            }

            return StepControlNode(
                    stepLocation,
                    branches)
        }
    }
}


data class StepControlNode(
        val step: ObjectLocation,
        val branches: List<BranchControlNode>
): ControlTree()


data class BranchControlNode(
        val nodes: List<StepControlNode>
): ControlTree() {
    companion object {
        val empty = BranchControlNode(listOf())
    }


    fun traverseDepthFirst(visitor: (ObjectLocation) -> Unit) {
        for (node in nodes) {
            visitor.invoke(node.step)

            for (branch in node.branches) {
                branch.traverseDepthFirst(visitor)
            }
        }
    }


    fun contains(target: ObjectLocation): Boolean {
        for (node in nodes) {
            if (node.step == target) {
                return true
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
            target: ObjectLocation
    ): List<ObjectLocation> {
        val buffer = mutableListOf<ObjectLocation>()
        predecessors(target, buffer)
        return buffer
    }


    private fun predecessors(
            target: ObjectLocation,
            buffer: MutableList<ObjectLocation>
    ) {
        for (node in nodes) {
            if (node.step == target) {
                break
            }

            for (branch in node.branches) {
                if (branch.contains(target)) {
                    branch.predecessors(target, buffer)
                    return
                }
            }

            buffer.add(node.step)
        }
    }
}

