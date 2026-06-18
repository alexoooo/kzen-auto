package tech.kzen.auto.client.objects.document.script.step.control.mapping

import tech.kzen.auto.client.objects.document.script.command.ScriptStepCommander
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class MappingStepCommander(
    private val mappingStepArchetype: ObjectLocation,
    private val itemArchetype: ObjectLocation
):
    ScriptStepCommander
{
    override fun archetypes(): Set<ObjectLocation> {
        return setOf(mappingStepArchetype)
    }


    override fun additionalCommands(
        insertedObjectLocation: ObjectLocation,
        insertedDocumentIndex: Int,
        graphStructure: GraphStructure
    ): List<NotationCommand> {
        val itemObjectLocation = ObjectLocation(
            insertedObjectLocation.documentPath,
            insertedObjectLocation.objectPath.nest(
                ScriptConventions.itemAttributePath, ObjectName("Item")))

        val itemNotation = ObjectNotation.ofParent(
            itemArchetype.objectPath.name)

        // Right after the just-added Mapping step, so the Item sits with its parent in document order.
        val itemCommand = AddObjectCommand(
            itemObjectLocation,
            PositionRelation.at(insertedDocumentIndex + 1),
            itemNotation)

        return listOf(itemCommand)
    }
}