package tech.kzen.auto.common.objects.document.custom.create

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions


data class CustomCreationSpec(
    val category: String,
    val label: String,
    val defaults: List<AttributeName>
) {
    companion object {
        private val categorySegment = AttributeSegment.ofKey("category")
        private val labelSegment = AttributeSegment.ofKey("label")
        private val defaultsSegment = AttributeSegment.ofKey("defaults")


        fun ofNotation(notation: MapAttributeNotation): CustomCreationSpec {
            val category = scalar(notation, categorySegment)
            require(category.isNotBlank()) { "Custom creation category must not be blank" }

            val label = scalar(notation, labelSegment)
            val defaultsNotation = notation.map[defaultsSegment] as? ListAttributeNotation
                ?: error("Custom creation defaults list not found")
            val defaults = defaultsNotation.values.map { entry ->
                val name = AttributeName((entry as? ScalarAttributeNotation)?.value
                    ?: error("Custom creation default must be an attribute name: $entry"))
                require(!NotationConventions.isSpecial(name) &&
                        name != CustomCreation.customCreateAttributeName) {
                    "Custom creation default must be configuration: $name"
                }
                name
            }
            require(defaults.distinct().size == defaults.size) {
                "Duplicate custom creation default: $defaults"
            }
            return CustomCreationSpec(category, label, defaults)
        }


        private fun scalar(notation: MapAttributeNotation, segment: AttributeSegment): String {
            return (notation.map[segment] as? ScalarAttributeNotation)?.value
                ?: error("Custom creation '$segment' scalar not found")
        }
    }


    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == CustomCreation.customCreateAttributeName) {
                "Unexpected attribute name: $attributeName"
            }
            val notation = graphStructure.graphNotation
                .firstAttribute(objectLocation, attributeName) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${CustomCreation.customCreateAttributeName}' attribute notation not found: $objectLocation")
            return AttributeDefinitionAttempt.success(ValueAttributeDefinition(ofNotation(notation)))
        }
    }
}
