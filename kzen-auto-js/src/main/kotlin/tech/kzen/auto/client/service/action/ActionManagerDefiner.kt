package tech.kzen.auto.client.service.action

import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.*
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.model.GraphNotation


@Suppress("unused")
class ActionManagerDefiner: ObjectDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            graphNotation: GraphNotation,
            graphMetadata: GraphMetadata,
            graphDefinition: GraphDefinition,
            graphInstance: GraphInstance
    ): ObjectDefinitionAttempt {
//        println("^^^^^^ ActionManager.Definer")
        val handleDefinitions = mutableListOf<ValueAttributeDefinition>()

        for (e in graphNotation.coalesce.values) {
            val isValue = e.value.attributes[NotationConventions.isAttribute.attribute]
                    ?.asString()
                    ?: continue

//            println("^^^^^^^^ isValue: ${e.key} - $isValue")
            if (isValue != ActionManager.actionParent) {
                continue
            }
//            println("^^^^^^^ adding: ${e.key}")

            handleDefinitions.add(ValueAttributeDefinition(e.key))
        }

        val handlesDefinition = ListAttributeDefinition(handleDefinitions)

        return ObjectDefinitionAttempt(
                ObjectDefinition(
                        ActionManager.className,
                        mapOf(ActionManager.actionsAttribute to handlesDefinition),
                        ActionManager.creatorReference,
                        setOf()
                ),
                setOf(),
                null
        )
    }
}