package tech.kzen.auto.common.objects.document.target

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.MapAttributeDefinition
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.context.environment.GraphEnvironment


/**
 * Instantiates a [TargetSpec] from the definition [TargetSpecDefiner] produced, dispatching to
 * the registered [TargetSpecType] whose name matches the defined `type` (autowired: adding a
 * target type requires no edit here).
 */
@Reflect
class TargetSpecCreator(
    private val types: List<TargetSpecType>
): AttributeCreator {
    override fun create(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        objectDefinition: ObjectDefinition,
        partialGraphInstance: GraphInstance,
        environment: GraphEnvironment
    ): Any {
        val attributeDefinition = objectDefinition.attributeDefinitions[attributeName]
            as? MapAttributeDefinition
            ?: throw IllegalArgumentException("Attribute definition missing: $objectLocation - $attributeName")

        val typeDefinition =
            attributeDefinition[TargetSpecDefiner.typeKey] as ValueAttributeDefinition
        val typeName = typeDefinition.value as String

        val type = types.find { it.typeName == typeName }
            ?: throw IllegalArgumentException(
                "Unknown target type: $typeName - $objectLocation - $attributeName")

        val policyName =
            (attributeDefinition[TargetMatchPolicy.policyKey] as? ValueAttributeDefinition)
                ?.value as? String
        val policyIndex =
            (attributeDefinition[TargetMatchPolicy.indexKey] as? ValueAttributeDefinition)
                ?.value as? Int

        val policy = TargetMatchPolicy.parse(policyName, policyIndex)
            ?: throw IllegalArgumentException(
                "Unknown target policy: $policyName - $objectLocation - $attributeName")

        val valueDefinition =
            attributeDefinition[TargetSpecDefiner.valueKey]

        return type.createSpec(
            valueDefinition, policy, objectLocation, partialGraphInstance)
    }
}
