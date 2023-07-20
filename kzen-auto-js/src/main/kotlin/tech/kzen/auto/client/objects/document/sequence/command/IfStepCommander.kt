package tech.kzen.auto.client.objects.document.sequence.command

import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectAtAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class IfStepCommander(
    private val stepArchetype: ObjectLocation,
    private val branchArchetype: ObjectLocation
):
    SequenceStepCommander
{
    override fun archetypes(): Set<ObjectLocation> {
        return setOf(stepArchetype)
//        return setOf()
    }


    override fun additionalCommands(
        createStepCommand: InsertObjectInListAttributeCommand,
        graphStructure: GraphStructure
    ): List<NotationCommand> {
        val containingObjectLocation = createStepCommand.insertedObjectLocation()

        // NB: +2 offset for main Script object plus parent
        val endOfDocumentPosition = graphStructure
            .graphNotation
            .documents[containingObjectLocation.documentPath]!!
            .objects
            .notations
            .values
            .size +
            1

        val objectNotation = ObjectNotation.ofParent(
            branchArchetype.objectPath.name
//            ObjectName("MultiSequenceStep")
        )

        val thenCommand = AddObjectAtAttributeCommand(
            containingObjectLocation,
            AttributeName.parse("then"),
            ObjectName("Branch"),
            PositionRelation.at(endOfDocumentPosition),
            objectNotation)

        val elseCommand = AddObjectAtAttributeCommand(
            containingObjectLocation,
            AttributeName.parse("else"),
            ObjectName("Branch"),
            PositionRelation.after(endOfDocumentPosition),
            objectNotation)

        return listOf(thenCommand, elseCommand)
    }
}