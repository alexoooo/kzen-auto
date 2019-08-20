package tech.kzen.auto.common.paradigm.imperative.util

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTree
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


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

        var pending: ObjectLocation? = null

        controlTree.traverseDepthFirst { stepLocation ->
            val objectPath = stepLocation.objectPath

            if (lastFrame.states[objectPath]?.phase() == ImperativePhase.Pending) {
                pending = stepLocation
            }
        }

        return pending
    }
}