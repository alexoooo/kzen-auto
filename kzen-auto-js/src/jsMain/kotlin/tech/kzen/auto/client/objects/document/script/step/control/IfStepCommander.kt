package tech.kzen.auto.client.objects.document.script.step.control

import tech.kzen.auto.client.objects.document.script.command.ScriptStepCommander
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.reflect.Reflect


/**
 * Seeds a ribbon-inserted IfStep with its first branch — the exact analogue of ForEachStepCommander seeding
 * the loop's `item` binding. Without it a fresh If would be an empty chain (valid, but else-only), and the
 * user would have to add a branch before the construct did anything.
 *
 * The fixed name "Branch" is safe at creation time — nothing can pre-exist under a just-created If — which is
 * why this does not go through ScriptCommander.findNextAvailable the way the "+ Else if" affordance does.
 */
@Reflect
class IfStepCommander(
    private val ifStepArchetype: ObjectLocation,
    private val branchArchetype: ObjectLocation
):
    ScriptStepCommander
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val branchesAttributePath = AttributePath.ofName(AttributeName("branches"))
        private val firstBranchName = ObjectName("Branch")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun archetypes(): Set<ObjectLocation> {
        return setOf(ifStepArchetype)
    }


    override fun additionalCommands(
        insertedObjectLocation: ObjectLocation,
        insertedDocumentIndex: Int,
        graphStructure: GraphStructure
    ): List<NotationCommand> {
        val branchObjectLocation = ObjectLocation(
            insertedObjectLocation.documentPath,
            insertedObjectLocation.objectPath.nest(
                branchesAttributePath, firstBranchName))

        val branchNotation = ObjectNotation.ofParent(
            branchArchetype.objectPath.name)

        // Right after the just-added If step, so the branch sits with its parent in document order — which
        // IS its order in the chain.
        val branchCommand = AddObjectCommand(
            branchObjectLocation,
            PositionRelation.at(insertedDocumentIndex + 1),
            branchNotation)

        return listOf(branchCommand)
    }
}
