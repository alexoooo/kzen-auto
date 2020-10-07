package tech.kzen.auto.common.objects.document.process

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
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.reflect.Reflect


data class PivotSpec(
    val rows: Set<String>,
//    val columns: List<String>,
    val values: Map<String, PivotValueSpec>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val rowsKey = AttributeSegment.ofKey("rows")
        val rowsAttributePath = ProcessConventions.pivotAttributePath.nest(rowsKey)

        private val valuesKey = AttributeSegment.ofKey("values")
        val valuesAttributePath = ProcessConventions.pivotAttributePath.nest(valuesKey)


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


//        fun rowsAttributePath(columnName: String): AttributePath {
//            val columnAttributePath = FilterSpec.columnAttributePath(columnName)
//            return columnAttributePath.nest(ColumnFilterSpec.valuesAttributeSegment)
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
            check(attributeName == ProcessConventions.pivotAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val rowsAttributeNotation = graphStructure
                .graphNotation
                .transitiveAttribute(objectLocation, rowsAttributePath) as? ListAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$rowsAttributePath' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val valuesAttributeNotation = graphStructure
                .graphNotation
                .transitiveAttribute(objectLocation, valuesAttributePath) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$valuesAttributePath' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val rows = rowsAttributeNotation
                .values
                .map { it.asString()!! }
                .toSet()

            val values = mutableMapOf<String, PivotValueSpec>()

            for (e in valuesAttributeNotation.values) {
                val pivotValueNotation = e.value as ListAttributeNotation
                val pivotValue = PivotValueSpec.ofNotation(pivotValueNotation)
                values[e.key.asString()] = pivotValue
            }

            val spec = PivotSpec(
                rows, values)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(spec))
        }
    }
}