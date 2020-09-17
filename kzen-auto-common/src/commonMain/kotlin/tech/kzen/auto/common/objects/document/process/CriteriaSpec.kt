package tech.kzen.auto.common.objects.document.process

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


data class CriteriaSpec(
    val columnRequiredValues: Map<String, Set<String>>
) {
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
                objectLocation: ObjectLocation,
                attributeName: AttributeName,
                graphStructure: GraphStructure,
                partialGraphDefinition: GraphDefinition,
                partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == FilterConventions.criteriaAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                    .graphNotation
                    .transitiveAttribute(objectLocation, FilterConventions.criteriaAttributeName) as? MapAttributeNotation
                    ?: return AttributeDefinitionAttempt.failure(
                            "'${FilterConventions.criteriaAttributeName}' attribute notation not found:" +
                                    " $objectLocation - $attributeName")

            val definitionMap = mutableMapOf<String, Set<String>>()

            for (e in attributeNotation.values) {
                val valueList = e.value as ListAttributeNotation
                definitionMap[e.key.asString()] = valueList.values.map { it.asString()!! }.toSet()
            }

            return AttributeDefinitionAttempt.success(
                    ValueAttributeDefinition(CriteriaSpec(definitionMap)))
        }
    }
}