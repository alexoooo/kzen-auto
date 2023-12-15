package tech.kzen.auto.client.objects.document.sequence.command

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
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
    companion object {
        fun findNextAvailable(
            containingObjectLocation: ObjectLocation,
            archetypeObjectLocation: ObjectLocation,
            graphStructure: GraphStructure
        ): ObjectName {
            val namePrefix = graphStructure
                .graphNotation
                .firstAttribute(archetypeObjectLocation, AutoConventions.titleAttributePath)
                ?.asString()
                ?: archetypeObjectLocation.objectPath.name.value

            val directObjectName = ObjectName(namePrefix)

            val documentObjectNames = graphStructure
                .graphNotation
                .documents[containingObjectLocation.documentPath]!!
                .objects
                .notations
                .map
                .keys
                .map { it.name }
                .toSet()

            if (directObjectName !in documentObjectNames) {
                return directObjectName
            }

            for (i in 2 .. 1000) {
                val numberedObjectName = ObjectName("$namePrefix $i")

                if (numberedObjectName !in documentObjectNames) {
                    return numberedObjectName
                }
            }

            return AutoConventions.randomAnonymous()
        }
    }

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
        containingAttributeLocation: AttributeLocation,
        indexInContainingAttribute: Int,
        archetypeObjectLocation: ObjectLocation,
        graphStructure: GraphStructure
    ): List<NotationCommand> {
        val newName = findNextAvailable(
            containingAttributeLocation.objectLocation, archetypeObjectLocation, graphStructure)

        // NB: +1 offset for main Script object
        val endOfDocumentPosition = graphStructure
            .graphNotation
            .documents[containingAttributeLocation.objectLocation.documentPath]!!
            .objects
            .notations
            .map
            .size

        val objectNotation = ObjectNotation.ofParent(
//            archetypeObjectLocation.toReference().name)
            archetypeObjectLocation.objectPath.name)

        val command = InsertObjectInListAttributeCommand(
            containingAttributeLocation.objectLocation,
            containingAttributeLocation.attributePath,
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