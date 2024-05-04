package tech.kzen.auto.common.objects.document.report.spec.filter

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabelMap
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class FilterSpec(
//    val columns: HeaderLabelMap<ColumnFilterSpec>
    val columns: Map<HeaderLabel, ColumnFilterSpec>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun addCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
            return InsertMapEntryInAttributeCommand(
                mainLocation,
                ReportConventions.filterAttributePath,
                PositionRelation.afterLast,
                columnAttributeSegment,
                ColumnFilterSpec.emptyNotation,
                true)
        }


        fun removeCommand(mainLocation: ObjectLocation, columnName: HeaderLabel): NotationCommand {
            return RemoveInAttributeCommand(
                mainLocation,
                columnAttributePath(columnName),
                true)
        }


        fun addValueCommand(
            mainLocation: ObjectLocation,
            columnName: HeaderLabel,
            filterValue: String
        ): NotationCommand {
            return InsertListItemInAttributeCommand(
                mainLocation,
                columnValuesAttributePath(columnName),
                PositionRelation.afterLast,
                ScalarAttributeNotation(filterValue))
        }


        fun removeValueCommand(
            mainLocation: ObjectLocation,
            columnName: HeaderLabel,
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
            columnName: HeaderLabel,
            filterType: ColumnFilterType
        ): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                columnTypeAttributePath(columnName),
                ScalarAttributeNotation(filterType.name))
        }


        private fun columnAttributePath(columnName: HeaderLabel): AttributePath {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName.asString())
            return ReportConventions.filterAttributePath.nest(columnAttributeSegment)
        }


        fun columnValuesAttributePath(columnName: HeaderLabel): AttributePath {
            val columnAttributePath = columnAttributePath(columnName)
            return columnAttributePath.nest(ColumnFilterSpec.valuesAttributeSegment)
        }


        private fun columnTypeAttributePath(columnName: HeaderLabel): AttributePath {
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
            check(attributeName == ReportConventions.filterAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, ReportConventions.filterAttributeName) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ReportConventions.filterAttributeName}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val definitionMap = mutableMapOf<HeaderLabel, ColumnFilterSpec>()

            for ((columnName, columnNotation) in attributeNotation.map) {
                val columnCriteriaNotation = columnNotation as MapAttributeNotation
                val columnCriteria = ColumnFilterSpec.ofNotation(columnCriteriaNotation)
                definitionMap[HeaderLabel.ofString(columnName.asKey())] = columnCriteria
            }

            return AttributeDefinitionAttempt.success(
                    ValueAttributeDefinition(FilterSpec(definitionMap)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toRunSignature(): FilterSpec {
        return FilterSpec(
            columns.filterValues { ! it.isEmpty() })
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUnorderedCollection(columns.entries) {
            addDigestible(it.key)
            addDigestible(it.value)
        }
    }
}