package tech.kzen.auto.common.objects.document.report.spec

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class PreviewSpec(
    val enabled: Boolean
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun enabledAttributePath(filtered: Boolean): AttributePath {
            val attributeName = when (filtered) {
                false -> ReportConventions.previewAllAttributeName
                true -> ReportConventions.previewFilteredAttributeName
            }

            return AttributePath.ofName(attributeName)
        }


        fun changeEnabledCommand(mainLocation: ObjectLocation, filtered: Boolean, enabled: Boolean): NotationCommand {
            val attributeName = when (filtered) {
                false -> ReportConventions.previewAllAttributeName
                true -> ReportConventions.previewFilteredAttributeName
            }

            return UpdateInAttributeCommand(
                mainLocation,
                AttributePath.ofName(attributeName),
                ScalarAttributeNotation(enabled.toString()))
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
            val attributeNotation = graphStructure
                    .graphNotation
                    .firstAttribute(objectLocation, attributeName) as? ScalarAttributeNotation
                    ?: return AttributeDefinitionAttempt.failure(
                            "'${attributeName}' attribute notation not found:" +
                                    " $objectLocation - $attributeName")

            val enabled = attributeNotation.asBoolean() ?: false
            return AttributeDefinitionAttempt.success(
                    ValueAttributeDefinition(PreviewSpec(enabled))
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addBoolean(enabled)
    }
}