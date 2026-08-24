package tech.kzen.auto.server.objects.job

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.context.environment.GraphEnvironment


/** Preserves one nullable nominal reference for snapshot-scoped resolution by its owning Worker. */
@Reflect
object NominalReferenceCreator: AttributeCreator {
    override fun create(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        objectDefinition: ObjectDefinition,
        partialGraphInstance: GraphInstance,
        environment: GraphEnvironment
    ): Any? {
        val definition = objectDefinition.attributeDefinitions[attributeName]
            as? ReferenceAttributeDefinition
            ?: throw IllegalArgumentException(
                "Nominal reference definition missing: $objectLocation - $attributeName")
        return definition.objectReference
    }
}
