package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


// Pure notation/graph helper for the RunStep -> linked sub-script ("instructions") relationship. No
// React, no services — just resolving the link.
object RunStepInstructions {
    //-----------------------------------------------------------------------------------------------------------------
    // The sub-script root linked by a RunStep's `instructions` attribute, or null when unset/unresolvable.
    fun instructionsLocation(
        graphNotation: GraphNotation,
        runStepLocation: ObjectLocation
    ): ObjectLocation? {
        val instructionsNotation = graphNotation
            .firstAttribute(runStepLocation, ScriptConventions.instructionsAttributeName)

        if (instructionsNotation !is ScalarAttributeNotation || instructionsNotation.value.isEmpty()) {
            return null
        }

        val reference = ObjectReference.parse(instructionsNotation.value)
        return graphNotation.coalesce.locateOptional(
            reference, ObjectReferenceHost.ofLocation(runStepLocation))
    }
}
