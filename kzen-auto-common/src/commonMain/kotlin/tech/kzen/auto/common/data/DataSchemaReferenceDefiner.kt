package tech.kzen.auto.common.data

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect


/**
 * Defines the optional DataSchema dependency as null when blank and as an ordinary strong graph reference when
 * present. This is intentionally not nominal: a selected schema joins the source's structural closure.
 */
@Reflect
object DataSchemaReferenceDefiner: AttributeDefiner {
    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val value = graphStructure.graphNotation
            .firstAttribute(objectLocation, attributeName)
            .asString()
            .orEmpty()
        return AttributeDefinitionAttempt.success(
            ReferenceAttributeDefinition(
                value.takeIf { it.isNotBlank() }?.let(ObjectReference::parse),
                weak = false,
                nullable = true))
    }
}
