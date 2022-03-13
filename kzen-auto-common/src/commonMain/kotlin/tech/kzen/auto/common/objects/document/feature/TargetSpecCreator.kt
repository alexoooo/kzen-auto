package tech.kzen.auto.common.objects.document.feature

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.MapAttributeDefinition
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class TargetSpecCreator: AttributeCreator {
    override fun create(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        objectDefinition: ObjectDefinition,
        partialGraphInstance: GraphInstance
    ): Any {
        val attributeDefinition = objectDefinition.attributeDefinitions.values[attributeName]
                as? MapAttributeDefinition
                ?: throw IllegalArgumentException("Attribute definition missing: $objectLocation - $attributeName")

        val typeDefinition =
                attributeDefinition.values[TargetSpecDefiner.typeKey] as ValueAttributeDefinition
        val typeName = typeDefinition.value as String
        val targetType = TargetType.valueOf(typeName)

        val valueDefinition =
                attributeDefinition.values[TargetSpecDefiner.valueKey]

        return when (targetType) {
            TargetType.Focus ->
                FocusTarget

            TargetType.Text -> {
                val value = (valueDefinition as ValueAttributeDefinition).value as String
                TextTarget(value)
            }

            TargetType.Xpath -> {
                val value = (valueDefinition as ValueAttributeDefinition).value as String
                XpathTarget(value)
            }

            TargetType.Visual -> {
                val value = (valueDefinition as ReferenceAttributeDefinition).objectReference!!

                val location = partialGraphInstance.objectInstances.locate(
                        value, ObjectReferenceHost.ofLocation(objectLocation))

                val featureDocument=
                        partialGraphInstance[location]?.reference as FeatureDocument

                VisualTarget(featureDocument)
            }
        }
    }
}