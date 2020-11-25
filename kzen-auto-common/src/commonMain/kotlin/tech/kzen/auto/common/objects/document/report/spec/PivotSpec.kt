package tech.kzen.auto.common.objects.document.report.spec

import tech.kzen.auto.common.objects.document.report.ReportConventions
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
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class PivotSpec(
    val rows: Set<String>,
//    val columns: List<String>,
    val values: PivotValueTableSpec
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val rowsKey = AttributeSegment.ofKey("rows")
        val rowsAttributePath = ReportConventions.pivotAttributePath.nest(rowsKey)

        private val valuesKey = AttributeSegment.ofKey("values")
        val valuesAttributePath = ReportConventions.pivotAttributePath.nest(valuesKey)


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
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == ReportConventions.pivotAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

//            graphStructure.graphNotation.mergeAttribute(
//                objectLocation, ProcessConventions.pivotAttributeName)

            val rowsAttributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, rowsAttributePath) as? ListAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$rowsAttributePath' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val valuesAttributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, valuesAttributePath) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$valuesAttributePath' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val rows = rowsAttributeNotation
                .values
                .map { it.asString()!! }
                .toSet()

            val values = PivotValueTableSpec.ofNotation(valuesAttributeNotation)

            val spec = PivotSpec(
                rows, values)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(spec))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return rows.isEmpty() &&
                values.isEmpty()
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleUnorderedList(rows.map { Digest.ofUtf8(it) })
        builder.addDigestible(values)
    }
}