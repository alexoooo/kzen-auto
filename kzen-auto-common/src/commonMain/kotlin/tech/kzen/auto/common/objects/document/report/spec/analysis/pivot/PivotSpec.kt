package tech.kzen.auto.common.objects.document.report.spec.analysis.pivot

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class PivotSpec(
    val rows: HeaderListing,
//    val columns: List<String>,
    val values: PivotValueTableSpec
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = PivotSpec(HeaderListing.empty, PivotValueTableSpec.empty)

        private val rowsKey = AttributeSegment.ofKey("rows")
        val rowsAttributePath = ReportConventions.pivotAttributePath.nest(rowsKey)

        private val valuesKey = AttributeSegment.ofKey("values")
        val valuesAttributePath = ReportConventions.pivotAttributePath.nest(valuesKey)


        fun ofNotation(attributeNotation: MapAttributeNotation): PivotSpec {
            val rowsNotation = attributeNotation
                .get(rowsKey) as? ListAttributeNotation
                ?: throw IllegalArgumentException("'$rowsKey' attribute notation not found")

            val valuesNotation = attributeNotation
                .get(valuesKey) as? MapAttributeNotation
                ?: throw IllegalArgumentException("'$valuesKey' attribute notation not found")

            val rows = rowsNotation
                .values
                .map { it.asString()!! }
                .toSet()

            val values = PivotValueTableSpec.ofNotation(valuesNotation)

            return PivotSpec(
                HeaderListing(rows.toList()),
                values)
        }


        fun addRowCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            return InsertListItemInAttributeCommand(
                mainLocation,
                rowsAttributePath,
                PositionRelation.afterLast,
                ScalarAttributeNotation(columnName))
        }


        fun removeRowCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            return RemoveListItemInAttributeCommand(
                mainLocation,
                rowsAttributePath,
                ScalarAttributeNotation(columnName),
                false)
        }


        fun clearRowCommand(mainLocation: ObjectLocation): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                rowsAttributePath,
                ListAttributeNotation.empty)
        }


        fun addValueCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            return InsertMapEntryInAttributeCommand(
                mainLocation,
                valuesAttributePath,
                PositionRelation.afterLast,
                AttributeSegment.ofKey(columnName),
                ListAttributeNotation.empty,
                true)
//            return UpdateInAttributeCommand(
//                mainLocation,
//                valuePath(columnName),
//                ListAttributeNotation.empty)
        }


        fun removeValueCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            return RemoveInAttributeCommand(
                mainLocation,
                valuePath(columnName),
                false)
        }


        fun addValueTypeCommand(
            mainLocation: ObjectLocation, columnName: String, valueType: PivotValueType
        ): NotationCommand {
            return InsertListItemInAttributeCommand(
                mainLocation,
                valuePath(columnName),
                PositionRelation.afterLast,
                ScalarAttributeNotation(valueType.name))
        }


        fun removeValueTypeCommand(
            mainLocation: ObjectLocation, columnName: String, valueType: PivotValueType
        ): NotationCommand {
            return RemoveListItemInAttributeCommand(
                mainLocation,
                valuePath(columnName),
                ScalarAttributeNotation(valueType.name),
                false)
        }


        private fun valuePath(columnName: String): AttributePath {
            return valuesAttributePath.nest(AttributeSegment.ofKey(columnName))
        }

//        private fun valueTypePath(columnName: String): AttributePath {
//            return valuesAttributePath.nest(AttributeSegment.ofKey(columnName))
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @Reflect
//    object Definer: AttributeDefiner {
//        override fun define(
//            objectLocation: ObjectLocation,
//            attributeName: AttributeName,
//            graphStructure: GraphStructure,
//            partialGraphDefinition: GraphDefinition,
//            partialGraphInstance: GraphInstance
//        ): AttributeDefinitionAttempt {
//            check(attributeName == ReportConventions.pivotAttributeName) {
//                "Unexpected attribute name: $attributeName"
//            }
//
////            graphStructure.graphNotation.mergeAttribute(
////                objectLocation, ProcessConventions.pivotAttributeName)
//
//            val rowsAttributeNotation = graphStructure
//                .graphNotation
//                .firstAttribute(objectLocation, rowsAttributePath) as? ListAttributeNotation
//                ?: return AttributeDefinitionAttempt.failure(
//                    "'$rowsAttributePath' attribute notation not found:" +
//                            " $objectLocation - $attributeName")
//
//            val valuesAttributeNotation = graphStructure
//                .graphNotation
//                .firstAttribute(objectLocation, valuesAttributePath) as? MapAttributeNotation
//                ?: return AttributeDefinitionAttempt.failure(
//                    "'$valuesAttributePath' attribute notation not found:" +
//                            " $objectLocation - $attributeName")
//
//            val rows = rowsAttributeNotation
//                .values
//                .map { it.asString()!! }
//                .toSet()
//
//            val values = PivotValueTableSpec.ofNotation(valuesAttributeNotation)
//
//            val spec = PivotSpec(
//                HeaderListing(rows.toList()), values)
//
//            return AttributeDefinitionAttempt.success(
//                ValueAttributeDefinition(spec))
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return rows.values.isEmpty() &&
                values.isEmpty()
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestible(rows)
        builder.addDigestible(values)
    }
}