package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


// Pure notation/graph helpers for the RunStep -> linked sub-script ("instructions") relationship,
// shared by the client trace fetch (ScriptProgressStore) and the RunStep display. No React, no
// services — just resolving the link and enumerating the sub-script's steps.
object RunStepInstructions {
    //-----------------------------------------------------------------------------------------------------------------
    fun isRunStep(
        graphNotation: GraphNotation,
        stepLocation: ObjectLocation
    ): Boolean {
        return graphNotation
            .inheritanceChain(stepLocation)
            .any { it.objectPath.name == ScriptConventions.runStepObjectName }
    }


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


    //-----------------------------------------------------------------------------------------------------------------
    // The instructions' step locations in document order (nested included), or empty when the target
    // isn't a Script document's main object (e.g. a RunStep pointing at an AdhocLogic/Custom object).
    fun subScriptStepLocations(
        graphDefinition: GraphDefinition,
        instructionsLocation: ObjectLocation
    ): List<ObjectLocation> {
        if (instructionsLocation.objectPath != ObjectPath.main) {
            return listOf()
        }

        val documentPath = instructionsLocation.documentPath
        val documentNotation = graphDefinition.graphStructure.graphNotation.documents[documentPath]
            ?: return listOf()
        if (!ScriptConventions.isScript(documentNotation)) {
            return listOf()
        }

        return ScriptTree
            .read(documentPath, graphDefinition)
            .orderedDescendantObjectPaths()
            .map { documentPath.toObjectLocation(it) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Every RunStep object in a document (nested included), used to discover sub-scripts to fetch traces for.
    fun runStepLocations(
        graphNotation: GraphNotation,
        documentPath: DocumentPath
    ): List<ObjectLocation> {
        val documentNotation = graphNotation.documents[documentPath]
            ?: return listOf()

        return documentNotation
            .objects
            .notations
            .map
            .keys
            .map { documentPath.toObjectLocation(it) }
            .filter { isRunStep(graphNotation, it) }
    }
}
