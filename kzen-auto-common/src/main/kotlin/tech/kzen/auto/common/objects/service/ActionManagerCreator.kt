package tech.kzen.auto.common.objects.service

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.ListAttributeDefinition
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.definition.ValueAttributeDefinition
import tech.kzen.lib.common.metadata.model.ObjectMetadata


@Suppress("unused")
class ActionManagerCreator: ObjectCreator {
    @Suppress("UNCHECKED_CAST")
    override fun create(
            objectLocation: ObjectLocation,
            objectDefinition: ObjectDefinition,
            objectMetadata: ObjectMetadata,
            graphInstance: GraphInstance
    ): Any {
        println("^^^^^^ ActionManager.Creator")
        val handlesDefinition = objectDefinition.attributeDefinitions[ActionManager.actionsAttribute] as ListAttributeDefinition

        val handles = mutableListOf<ObjectLocation>()

        for (actionLocation in handlesDefinition.values as List<ValueAttributeDefinition>) {
            handles.add(actionLocation.value as ObjectLocation)
        }

        return ActionManager(handles)
    }
}