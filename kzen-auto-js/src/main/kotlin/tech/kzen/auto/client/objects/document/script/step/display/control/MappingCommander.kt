package tech.kzen.auto.client.objects.document.script.step.display.control

import tech.kzen.auto.client.objects.document.script.command.StepCommander
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand


@Suppress("unused")
class MappingCommander(
        private val stepArchetype: ObjectLocation,
        private val itemArchetype: ObjectLocation
): StepCommander {
    override fun archetypes(): Set<ObjectLocation> {
        return setOf(stepArchetype)
    }

    override fun additionalCommands(
            createStepCommand: InsertObjectInListAttributeCommand,
            graphStructure: GraphStructure
    ): List<NotationCommand> {
        val containingObjectLocation = createStepCommand.insertedObjectLocation()

        val newName = ScriptDocument.findNextAvailable(
                containingObjectLocation, itemArchetype, graphStructure)

        // NB: +1 offset for main Script object
        val endOfDocumentPosition = graphStructure
                .graphNotation
                .documents[containingObjectLocation.documentPath]!!
                .objects
                .notations
                .values
                .size

        val objectNotation = ObjectNotation.ofParent(
                itemArchetype.objectPath.name)

        val command = InsertObjectInListAttributeCommand(
                containingObjectLocation,
                ScriptDocument.stepsAttributePath,
                PositionIndex(0),
                newName,
                PositionIndex(endOfDocumentPosition),
                objectNotation)

        return listOf(command)
    }
}