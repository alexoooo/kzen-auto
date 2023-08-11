package tech.kzen.auto.client.objects.document.script.command

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand


interface StepCommander {
    fun archetypes(): Set<ObjectLocation>


    fun additionalCommands(
            createStepCommand: InsertObjectInListAttributeCommand,
            graphStructure: GraphStructure
    ): List<NotationCommand>
}