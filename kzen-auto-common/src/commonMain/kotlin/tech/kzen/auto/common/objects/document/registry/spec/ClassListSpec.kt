package tech.kzen.auto.common.objects.document.registry.spec

import tech.kzen.auto.common.objects.document.registry.ObjectRegistryConventions
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassName


data class ClassListSpec(
    val classNames: List<ClassName>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofAttributeNotation(attributeNotation: ListAttributeNotation): ClassListSpec {
            val builder = mutableListOf<ClassName>()

            for (classNameNotation in attributeNotation.values) {
                val fullyQualifiedClassName = (classNameNotation as ScalarAttributeNotation).value
                builder.add(ClassName(fullyQualifiedClassName))
            }

            return ClassListSpec(builder)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == ObjectRegistryConventions.classesAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .firstAttribute(
                    objectLocation, ObjectRegistryConventions.classesAttributeName
                ) as? ListAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ObjectRegistryConventions.classesAttributeName}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val fieldFormatListSpec = ofAttributeNotation(attributeNotation)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(fieldFormatListSpec))
        }
    }
}