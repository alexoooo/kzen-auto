package tech.kzen.auto.common.objects.document.data.spec

import tech.kzen.auto.common.objects.document.data.DataFormatConventions
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertMapEntryInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.reflect.Reflect


data class FieldFormatListSpec(
    val fields: Map<String, FieldFormatSpec>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofAttributeNotation(attributeNotation: MapAttributeNotation): FieldFormatListSpec {
            val builder = mutableMapOf<String, FieldFormatSpec>()

            for ((fieldName, fieldNotation) in attributeNotation.map) {
                val fieldFormat = FieldFormatSpec.ofNotation(fieldNotation as MapAttributeNotation)
                builder[fieldName.asKey()] = fieldFormat
            }

            return FieldFormatListSpec(builder)
        }


        fun addCommand(mainLocation: ObjectLocation, fieldName: String): NotationCommand {
            val fieldNameAttributeSegment = AttributeSegment.ofKey(fieldName)
            return InsertMapEntryInAttributeCommand(
                mainLocation,
                DataFormatConventions.fieldsAttributePath,
                PositionRelation.afterLast,
                fieldNameAttributeSegment,
                FieldFormatSpec.any.asNotation(),
                true)
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
            check(attributeName == DataFormatConventions.fieldsAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, DataFormatConventions.fieldsAttributeName) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${DataFormatConventions.fieldsAttributeName}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val fieldFormatListSpec = ofAttributeNotation(attributeNotation)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(fieldFormatListSpec))
        }
    }
}
