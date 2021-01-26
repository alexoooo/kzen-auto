package tech.kzen.auto.common.objects.document.report.spec

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.reflect.Reflect


data class InputSpec(
    val directory: String,
    val selected: List<String>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val directoryKey = AttributeSegment.ofKey("directory")
        val directoryAttributePath = ReportConventions.inputAttributePath.nest(directoryKey)

        private val selectedKey = AttributeSegment.ofKey("selected")
//        val selectedAttributePath = ReportConventions.inputAttributePath.nest(selectedKey)


        fun browseCommand(mainLocation: ObjectLocation, directory: String): UpdateInAttributeCommand{
            return UpdateInAttributeCommand(
                mainLocation,
                directoryAttributePath,
                ScalarAttributeNotation(directory)
            )
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
            check(attributeName == ReportConventions.inputAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, ReportConventions.inputAttributeName) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ReportConventions.inputAttributeName}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val directory = attributeNotation.get(directoryKey)?.asString()!!
            val selected = (attributeNotation.get(selectedKey) as ListAttributeNotation).values.map { it.asString()!! }

            val inputSpec = InputSpec(directory, selected)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(inputSpec))
        }
    }
}