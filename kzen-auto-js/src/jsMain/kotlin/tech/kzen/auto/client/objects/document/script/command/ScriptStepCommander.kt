package tech.kzen.auto.client.objects.document.script.command

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand


interface ScriptStepCommander {
    fun archetypes(): Set<ObjectLocation>


    // Commands to run after the step object has been added (e.g. seed nested children). insertedObjectLocation
    // is the new step's location; insertedDocumentIndex is the document index it was inserted at.
    fun additionalCommands(
        insertedObjectLocation: ObjectLocation,
        insertedDocumentIndex: Int,
        graphStructure: GraphStructure
    ): List<NotationCommand>
}