package tech.kzen.auto.common.objects.document.process

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.collect.persistentListOf


data class CriteriaSpec(
    val columns: Map<String, ColumnCriteria>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun addCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
            return InsertMapEntryInAttributeCommand(
                mainLocation,
                FilterConventions.criteriaAttributePath,
                PositionRelation.at(0),
                columnAttributeSegment,
                ColumnCriteria.emptyNotation,
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
            criteriaType: ColumnCriteriaType
        ): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                columnTypeAttributePath(columnName),
                ScalarAttributeNotation(criteriaType.name))
        }


        private fun columnAttributePath(columnName: String): AttributePath {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
            return FilterConventions.criteriaAttributePath.nest(columnAttributeSegment)
        }


        fun columnValuesAttributePath(columnName: String): AttributePath {
            val columnAttributePath = columnAttributePath(columnName)
            return columnAttributePath.nest(ColumnCriteria.valuesAttributeSegment)
        }

        fun columnTypeAttributePath(columnName: String): AttributePath {
            val columnAttributePath = columnAttributePath(columnName)
            return columnAttributePath.nest(ColumnCriteria.typeAttributeSegment)
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
            check(attributeName == FilterConventions.criteriaAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                    .graphNotation
                    .transitiveAttribute(objectLocation, FilterConventions.criteriaAttributeName) as? MapAttributeNotation
                    ?: return AttributeDefinitionAttempt.failure(
                            "'${FilterConventions.criteriaAttributeName}' attribute notation not found:" +
                                    " $objectLocation - $attributeName")

            val definitionMap = mutableMapOf<String, ColumnCriteria>()

            for (e in attributeNotation.values) {
                val columnCriteriaNotation = e.value as MapAttributeNotation
                val columnCriteria = ColumnCriteria.ofNotation(columnCriteriaNotation)
                definitionMap[e.key.asString()] = columnCriteria
            }

            return AttributeDefinitionAttempt.success(
                    ValueAttributeDefinition(CriteriaSpec(definitionMap)))
        }
    }
}