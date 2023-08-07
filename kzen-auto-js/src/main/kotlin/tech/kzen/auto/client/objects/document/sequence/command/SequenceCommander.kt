package tech.kzen.auto.client.objects.document.sequence.command

import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class SequenceCommander(
    stepCommanders: List<SequenceStepCommander>
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val byArchetype: Map<ObjectLocation, SequenceStepCommander>


    //-----------------------------------------------------------------------------------------------------------------
    init {
        val builder = mutableMapOf<ObjectLocation, SequenceStepCommander>()
        for (stepCommander in stepCommanders) {
            for (archetype in stepCommander.archetypes()) {
                builder[archetype] = stepCommander
            }
        }
        byArchetype = builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun createCommands(
        containingObjectLocation: ObjectLocation,
        containingAttributePath: AttributePath,
        indexInContainingAttribute: Int,
        archetypeObjectLocation: ObjectLocation,
        graphStructure: GraphStructure
    ): List<NotationCommand> {
        val newName = ScriptDocument.findNextAvailable(
            containingObjectLocation, archetypeObjectLocation, graphStructure)

        // NB: +1 offset for main Script object
        val endOfDocumentPosition = graphStructure
            .graphNotation
            .documents[containingObjectLocation.documentPath]!!
            .objects
            .notations
            .values
            .size

        val objectNotation = ObjectNotation.ofParent(
            archetypeObjectLocation.toReference().name)

        val command = InsertObjectInListAttributeCommand(
            containingObjectLocation,
            containingAttributePath,
            PositionRelation.at(indexInContainingAttribute),
            newName,
            PositionRelation.at(endOfDocumentPosition),
            objectNotation)

        val stepCommander = byArchetype[archetypeObjectLocation]

        val commands =
            if (stepCommander != null) {
                val additionalCommands =
                    stepCommander.additionalCommands(command, graphStructure)

                listOf(command) + additionalCommands
            }
            else {
                listOf(command)
            }

        return commands
    }
}