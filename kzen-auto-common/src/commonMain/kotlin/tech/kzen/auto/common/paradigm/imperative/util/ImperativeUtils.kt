package tech.kzen.auto.common.paradigm.imperative.util
//
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativeFrame
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
//import tech.kzen.auto.common.paradigm.imperative.model.control.*
//import tech.kzen.lib.common.model.location.ObjectLocation
//import tech.kzen.lib.common.model.obj.ObjectPath
//import tech.kzen.lib.common.model.structure.GraphStructure
//
//
//object ImperativeUtils {
//    fun next(
//            graphStructure: GraphStructure,
//            executionModel: ImperativeModel
//    ): ObjectLocation? {
//        if (executionModel.frames.isEmpty() || executionModel.isRunning()) {
//            return null
//        }
//
//        val lastFrame = executionModel.frames.last()
//
//        for (e in lastFrame.states) {
//            if (e.value.phase(false) == ImperativePhase.Error) {
//                return null
//            }
//        }
//
//        val host = lastFrame.path
//        val controlTree = ControlTree.readSteps(graphStructure, host)
//
//        val next = nextInBranch(controlTree, lastFrame)
//
//        return next?.let { ObjectLocation(host, it) }
//    }
//
//
//    private fun nextInBranch(
//            branchControlNode: BranchControlNode,
//            imperativeFrame: ImperativeFrame
//    ): ObjectPath? {
//        for (node in branchControlNode.nodes) {
//            val state = imperativeFrame.states[node.step]
//                    ?: continue
//
//            if (state.previous != null) {
//                continue
//            }
//
//            val isNext = when (
//                val controlState = state.controlState
//            ) {
//                null -> true
//                InitialControlState -> true
//                is FinalControlState -> true
//
//                // TODO
//                is InvokeControlState -> true
//
//                is InternalControlState -> {
//                    val branch = (node as BranchingControlNode).branches[controlState.branchIndex]
//
//                    val nextInBranch = nextInBranch(branch, imperativeFrame)
//
//                    if (nextInBranch != null) {
//                        return nextInBranch
//                    }
//
//                    true
//                }
//            }
//
//            if (isNext) {
//                return node.step
//            }
//        }
//
//        return null
//    }
//}