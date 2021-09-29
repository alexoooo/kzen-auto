package tech.kzen.auto.common.objects.document.report.spec

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertMapEntryInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class FormulaSpec(
    val formulas: Map<String, String>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun addCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
            return InsertMapEntryInAttributeCommand(
                mainLocation,
                ReportConventions.formulaAttributePath,
                PositionRelation.afterLast,
                columnAttributeSegment,
                ScalarAttributeNotation(""),
                true)
        }


        fun removeCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            return RemoveInAttributeCommand(
                mainLocation,
                formulaAttributePath(columnName),
                true)
        }


        fun updateFormulaCommand(
            mainLocation: ObjectLocation,
            columnName: String,
            formula: String
        ): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                formulaAttributePath(columnName),
                ScalarAttributeNotation(formula))
        }


        fun formulaAttributePath(columnName: String): AttributePath {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
            return ReportConventions.formulaAttributePath.nest(columnAttributeSegment)
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
            check(attributeName == ReportConventions.formulaAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                    .graphNotation
                    .firstAttribute(objectLocation, ReportConventions.formulaAttributeName) as? MapAttributeNotation
                    ?: return AttributeDefinitionAttempt.failure(
                            "'${ReportConventions.formulaAttributeName}' attribute notation not found:" +
                                    " $objectLocation - $attributeName")

            val definitionMap = mutableMapOf<String, String>()

            for (e in attributeNotation.values) {
                definitionMap[e.key.asString()] = e.value.asString()
                    ?: return AttributeDefinitionAttempt.failure(
                    "'${e.key}' is not a String: ${e.value}")
            }

            return AttributeDefinitionAttempt.success(
                    ValueAttributeDefinition(FormulaSpec(definitionMap)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun headerListing(): HeaderListing {
        return HeaderListing(formulas.keys.toList())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUnorderedCollection(formulas.entries) {
            addUtf8(it.key)
            addUtf8(it.value)
        }
    }
}