package tech.kzen.auto.common.objects.document.process

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
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.reflect.Reflect


data class FilterSpec(
    val columns: Map<String, ColumnFilterSpec>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun addCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
            return InsertMapEntryInAttributeCommand(
                mainLocation,
                ProcessConventions.filterAttributePath,
                PositionRelation.at(0),
                columnAttributeSegment,
                ColumnFilterSpec.emptyNotation,
                true)
        }


        fun removeCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            return RemoveInAttributeCommand(
                mainLocation,
                columnAttributePath(columnName),
                true)
        }


        fun addValueCommand(mainLocation: ObjectLocation, columnName: String, filterValue: String): NotationCommand {
            return InsertListItemInAttributeCommand(
                mainLocation,
                columnValuesAttributePath(columnName),
                PositionRelation.afterLast,
                ScalarAttributeNotation(filterValue))
        }


        fun removeValueCommand(
            mainLocation: ObjectLocation,
            columnName: String,
            filterValue: String
        ): NotationCommand {
            return RemoveListItemInAttributeCommand(
                mainLocation,
                columnValuesAttributePath(columnName),
                ScalarAttributeNotation(filterValue),
                false)
        }


        fun updateTypeCommand(
            mainLocation: ObjectLocation,
            columnName: String,
            filterType: ColumnFilterType
        ): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                columnTypeAttributePath(columnName),
                ScalarAttributeNotation(filterType.name))
        }


        private fun columnAttributePath(columnName: String): AttributePath {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
            return ProcessConventions.filterAttributePath.nest(columnAttributeSegment)
        }


        fun columnValuesAttributePath(columnName: String): AttributePath {
            val columnAttributePath = columnAttributePath(columnName)
            return columnAttributePath.nest(ColumnFilterSpec.valuesAttributeSegment)
        }

        fun columnTypeAttributePath(columnName: String): AttributePath {
            val columnAttributePath = columnAttributePath(columnName)
            return columnAttributePath.nest(ColumnFilterSpec.typeAttributeSegment)
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
            check(attributeName == ProcessConventions.filterAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                    .graphNotation
                    .transitiveAttribute(objectLocation, ProcessConventions.filterAttributeName) as? MapAttributeNotation
                    ?: return AttributeDefinitionAttempt.failure(
                            "'${ProcessConventions.filterAttributeName}' attribute notation not found:" +
                                    " $objectLocation - $attributeName")

            val definitionMap = mutableMapOf<String, ColumnFilterSpec>()

            for (e in attributeNotation.values) {
                val columnCriteriaNotation = e.value as MapAttributeNotation
                val columnCriteria = ColumnFilterSpec.ofNotation(columnCriteriaNotation)
                definitionMap[e.key.asString()] = columnCriteria
            }

            return AttributeDefinitionAttempt.success(
                    ValueAttributeDefinition(FilterSpec(definitionMap)))
        }
    }
}