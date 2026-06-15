package tech.kzen.auto.common.objects.document.script

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


// Coerces a 'closePolicy' notation scalar into a ResourceClosePolicy at definition time (once), so the
// opening step's constructor receives the enum directly and an invalid value fails definition rather
// than execution. Bound via the ResourceClosePolicy type's meta.ref (mirrors TargetSpecDefiner).
@Reflect
class ResourceClosePolicyDefiner: AttributeDefiner {
    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val notation = graphStructure
            .graphNotation
            .firstAttribute(objectLocation, attributeName) as? ScalarAttributeNotation
            ?: return AttributeDefinitionAttempt.failure(
                "'closePolicy' must be a scalar: $objectLocation - $attributeName")

        return try {
            AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(ResourceClosePolicy.parse(notation.value)))
        }
        catch (e: IllegalArgumentException) {
            AttributeDefinitionAttempt.failure(e.message ?: "Invalid closePolicy: ${notation.value}")
        }
    }
}
