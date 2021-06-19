package tech.kzen.auto.common.objects.document.report.spec

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class PreviewSpec(
    val enabled: Boolean
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        fun addCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
//            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
//            return InsertMapEntryInAttributeCommand(
//                mainLocation,
//                ReportConventions.formulaAttributePath,
//                PositionRelation.afterLast,
//                columnAttributeSegment,
//                ScalarAttributeNotation(""),
//                true)
//        }
//
//
//        fun removeCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
//            return RemoveInAttributeCommand(
//                mainLocation,
//                formulaAttributePath(columnName),
//                true)
//        }
//
//
//        fun updateFormulaCommand(
//            mainLocation: ObjectLocation,
//            columnName: String,
//            formula: String
//        ): NotationCommand {
//            return UpdateInAttributeCommand(
//                mainLocation,
//                formulaAttributePath(columnName),
//                ScalarAttributeNotation(formula))
//        }
//
//
//        fun formulaAttributePath(columnName: String): AttributePath {
//            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
//            return ReportConventions.formulaAttributePath.nest(columnAttributeSegment)
//        }
//    }


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


//    //-----------------------------------------------------------------------------------------------------------------
//    fun headerListing(): HeaderListing {
//        return HeaderListing(formulas.keys.toList())
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addBoolean(enabled)
//        builder.addDigestibleUnorderedMap(
//            formulas
//                .map {
//                    Digest.ofUtf8(it.key) to Digest.ofUtf8(it.value)
//                }
//                .toMap())
    }
}