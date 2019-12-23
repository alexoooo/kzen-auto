package tech.kzen.auto.common.paradigm.imperative.util

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeFrame
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
import tech.kzen.auto.common.paradigm.imperative.model.control.*
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


object ImperativeUtils {
    fun next(
            graphStructure: GraphStructure,
            executionModel: ImperativeModel
    ): ObjectLocation? {
        if (executionModel.frames.isEmpty() || executionModel.containsStatus(ImperativePhase.Running)) {
            return null
        }

        val lastFrame = executionModel.frames.last()

        for (e in lastFrame.states) {
            if (e.value.phase() == ImperativePhase.Error) {
                return null
            }
        }

        val controlTree = ControlTree.readSteps(graphStructure, lastFrame.path)

        return nextInBranch(controlTree, lastFrame)

//        var pending: ObjectLocation? = null
//
//        controlTree.traverseDepthFirst { stepLocation ->
//            val objectPath = stepLocation.objectPath
//
//            if (lastFrame.states[objectPath]?.phase() == ImperativePhase.Pending) {
//                pending = stepLocation
//            }
//        }
//
//        return pending
    }


    private fun nextInBranch(
            branchControlNode: BranchControlNode,
            imperativeFrame: ImperativeFrame
    ): ObjectLocation? {
        for (node in branchControlNode.nodes) {
            val state = imperativeFrame.states[node.step.objectPath]
                    ?: continue

            if (state.previous != null) {
                continue
            }

            val isNext = when (
                val controlState = state.controlState
            ) {
                null -> true
                InitialControlState -> true
                FinalControlState -> true

                is InternalControlState -> {
                    val branch = node.branches[controlState.branchIndex]

                    val nextInBranch = nextInBranch(branch, imperativeFrame)

                    if (nextInBranch != null) {
                        return nextInBranch
                    }

                    true
                }
            }

            if (isNext) {
                return node.step
            }
        }

        return null
    }
}