package tech.kzen.auto.client.objects.document.script.command

import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand


class ScriptCommander(
        stepCommanders: List<StepCommander>
) {
//    companion object {
//        fun createAsync() {
//
//        }
//    }


    private val byArchetype: Map<ObjectLocation, StepCommander>


    init {
        val builder = mutableMapOf<ObjectLocation, StepCommander>()
        for (stepCommander in stepCommanders) {
            for (archetype in stepCommander.archetypes()) {
                builder[archetype] = stepCommander
            }
        }
        byArchetype = builder
    }


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
                PositionIndex(indexInContainingAttribute),
                newName,
                PositionIndex(endOfDocumentPosition),
                objectNotation)

        val stepCommander = byArchetype[archetypeObjectLocation]

        @Suppress("UnnecessaryVariable")
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