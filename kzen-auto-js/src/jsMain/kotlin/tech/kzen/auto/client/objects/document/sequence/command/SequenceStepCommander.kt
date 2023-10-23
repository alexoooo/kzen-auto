package tech.kzen.auto.client.objects.document.sequence.command

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand


interface SequenceStepCommander {
    fun archetypes(): Set<ObjectLocation>


    fun additionalCommands(
        createStepCommand: InsertObjectInListAttributeCommand,
        graphStructure: GraphStructure
    ): List<NotationCommand>
}