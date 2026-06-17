package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
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


    //-----------------------------------------------------------------------------------------------------------------
    // The instructions roots reachable from this RunStep — its own instructions sub-script plus,
    // recursively, every nested RunStep's instructions. Trace events are keyed by their execution's
    // root object, so this is the set used to attribute the whole subtree's events to the RunStep.
    // The visited-set guards cycles / shared sub-scripts.
    fun subtreeInstructionRoots(
        graphNotation: GraphNotation,
        runStepLocation: ObjectLocation
    ): List<ObjectLocation> {
        val out = mutableListOf<ObjectLocation>()
        collectSubtreeInstructionRoots(graphNotation, runStepLocation, mutableSetOf(), out)
        return out
    }


    private fun collectSubtreeInstructionRoots(
        graphNotation: GraphNotation,
        runStepLocation: ObjectLocation,
        visited: MutableSet<DocumentPath>,
        out: MutableList<ObjectLocation>
    ) {
        val instructionsLocation = instructionsLocation(graphNotation, runStepLocation)
            ?: return
        out.add(instructionsLocation)

        val instructionsDocumentPath = instructionsLocation.documentPath
        if (instructionsDocumentPath in visited) {
            return
        }
        visited.add(instructionsDocumentPath)

        for (nested in runStepLocations(graphNotation, instructionsDocumentPath)) {
            collectSubtreeInstructionRoots(graphNotation, nested, visited, out)
        }
    }
}
