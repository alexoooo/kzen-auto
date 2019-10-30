package tech.kzen.auto.common.objects.document.script.target

import tech.kzen.auto.common.objects.document.feature.TargetType
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation


class TargetSpecDefiner: AttributeDefiner {
    companion object {
        val targetAttributeName = AttributeName("target")

        val typeKey = "type"
        val valueKey = "value"
    }


    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        check(attributeName == targetAttributeName) {
            "Unexpected attribute name: $attributeName"
        }

        val targetNotation = graphStructure
                .graphNotation
                .transitiveAttribute(objectLocation, targetAttributeName)
                as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                        "'Target' attribute notation not found: $objectLocation - $attributeName")

        val typeName = targetNotation.get(typeKey)?.asString()
                ?: return AttributeDefinitionAttempt.failure(
                        "Target 'type' not found: $objectLocation")

        @Suppress("MoveVariableDeclarationIntoWhen")
        val targetType = TargetType.valueOf(typeName)

        val valueDefinition = when (targetType) {
            TargetType.Focus ->
                null

            TargetType.Text -> {
                val value = targetNotation.get(valueKey)?.asString()
                        ?: return AttributeDefinitionAttempt.failure(
                                "Target text 'value' not found: $objectLocation")
//
//                ValueAttributeDefinition(TextTarget(value))
                ValueAttributeDefinition(value)
            }

            TargetType.Xpath -> {
                val value = targetNotation.get(valueKey)?.asString()
                        ?: return AttributeDefinitionAttempt.failure(
                                "Target xpath 'value' not found: $objectLocation")

                ValueAttributeDefinition(value)
            }

            TargetType.Visual -> {
                val value = targetNotation.get(valueKey)?.asString()
                        ?: return AttributeDefinitionAttempt.failure(
                                "Target visual 'value' not found: $objectLocation")

                ReferenceAttributeDefinition(ObjectReference.parse(value))
            }
        }

        val definitionMap = mutableMapOf<String, AttributeDefinition>()
        definitionMap[typeKey] = ValueAttributeDefinition(typeName)

        if (valueDefinition != null) {
            definitionMap[typeKey] = valueDefinition
        }

        return AttributeDefinitionAttempt.success(
                MapAttributeDefinition(definitionMap))
    }
}