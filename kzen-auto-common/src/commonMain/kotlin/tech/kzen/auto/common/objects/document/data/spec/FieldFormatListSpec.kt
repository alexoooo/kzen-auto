package tech.kzen.auto.common.objects.document.data.spec

import tech.kzen.auto.common.objects.document.data.DataFormatConventions
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


data class FieldFormatListSpec(
    val fields: Map<String, FieldFormatSpec>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {

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
            check(attributeName == DataFormatConventions.fieldsAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, DataFormatConventions.fieldsAttributeName) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${DataFormatConventions.fieldsAttributeName}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val builder = mutableMapOf<String, FieldFormatSpec>()

            for ((fieldName, fieldNotation) in attributeNotation.values) {
                val fieldFormat = FieldFormatSpec.ofNotation(fieldNotation as MapAttributeNotation)
                builder[fieldName.asKey()] = fieldFormat
            }

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(FieldFormatListSpec(builder))
            )
        }
    }
}
