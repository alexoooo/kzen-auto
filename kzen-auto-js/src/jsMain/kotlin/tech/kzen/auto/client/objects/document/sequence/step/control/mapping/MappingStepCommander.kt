package tech.kzen.auto.client.objects.document.sequence.step.control.mapping

import tech.kzen.auto.client.objects.document.sequence.command.SequenceStepCommander
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class MappingStepCommander(
    private val mappingStepArchetype: ObjectLocation,
    private val itemArchetype: ObjectLocation
):
    SequenceStepCommander
{
    override fun archetypes(): Set<ObjectLocation> {
        return setOf(mappingStepArchetype)
    }


    override fun additionalCommands(
        createStepCommand: InsertObjectInListAttributeCommand,
        graphStructure: GraphStructure
    ): List<NotationCommand> {
        val containingObjectLocation = createStepCommand.insertedObjectLocation()

        val endOfDocumentPosition = graphStructure
            .graphNotation
            .documents[containingObjectLocation.documentPath]!!
            .objects
            .notations
            .values
            .size +
            1

        val itemNotation = ObjectNotation.ofParent(
            itemArchetype.objectPath.name)

        val itemCommand = InsertObjectInListAttributeCommand(
            containingObjectLocation,
            SequenceConventions.stepsAttributePath,
            PositionRelation.first,
            ObjectName("Item"),
            PositionRelation.at(endOfDocumentPosition),
            itemNotation)

        return listOf(itemCommand)
    }
}