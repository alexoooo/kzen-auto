package tech.kzen.auto.common.paradigm.imperative.util

import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation


object ImperativeUtils {
    fun next(
            graphNotation: GraphNotation,
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

        // TODO: consolidate with ScriptController
        val mainObjectLocation = ObjectLocation(lastFrame.path, NotationConventions.mainObjectPath)
        val stepsNotation = graphNotation
                .transitiveAttribute(mainObjectLocation, ScriptDocument.stepsAttributePath)
                as? ListAttributeNotation
                ?: return null

        val stepReferences = stepsNotation.values.map { ObjectReference.parse(it.asString()!!) }

        for (stepReference in stepReferences) {
            val stepLocation = graphNotation.coalesce.locate(stepReference)
            val objectPath = stepLocation.objectPath

            if (lastFrame.states[objectPath]?.phase() == ImperativePhase.Pending) {
                return stepLocation
            }
        }

        return null
    }
}