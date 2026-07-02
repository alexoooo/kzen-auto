package tech.kzen.auto.common.objects.document.report.spec.analysis.pivot

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
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
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


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

        // The Job PivotWorker carries its pivot config as a top-level `pivot` attribute (defined by [Definer]);
        // the Report document instead nests it under `analysis.pivot` (defined by AnalysisSpec.Definer).
        val pivotAttributeName = AttributeName("pivot")

        private val rowsKey = AttributeSegment.ofKey("rows")
        private val rowsAttributePath = ReportConventions.pivotAttributePath.nest(rowsKey)

        private val valuesKey = AttributeSegment.ofKey("values")
        private val valuesAttributePath = ReportConventions.pivotAttributePath.nest(valuesKey)


        fun ofNotation(attributeNotation: MapAttributeNotation): PivotSpec {
            val rowsNotation = attributeNotation[rowsKey] as? ListAttributeNotation
                ?: throw IllegalArgumentException("'$rowsKey' attribute notation not found")

            val rows = rowsNotation
                .values
                .map { HeaderLabel.ofString(it.asString()!!) }
                .toSet()

            val valuesNotation = attributeNotation[valuesKey] as? MapAttributeNotation
                ?: throw IllegalArgumentException("'$valuesKey' attribute notation not found")

            val values = PivotValueTableSpec.ofNotation(valuesNotation)

            return PivotSpec(
                HeaderListing(rows.toList()),
                values)
        }


        fun addRowCommand(mainLocation: ObjectLocation, headerLabel: HeaderLabel): NotationCommand {
            return InsertListItemInAttributeCommand(
                mainLocation,
                rowsAttributePath,
                PositionRelation.afterLast,
                ScalarAttributeNotation(headerLabel.asString()))
        }


        fun removeRowCommand(mainLocation: ObjectLocation, headerLabel: HeaderLabel): NotationCommand {
            return RemoveListItemInAttributeCommand(
                mainLocation,
                rowsAttributePath,
                ScalarAttributeNotation(headerLabel.asString()),
                false)
        }


        fun clearRowCommand(mainLocation: ObjectLocation): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                rowsAttributePath,
                ListAttributeNotation.empty)
        }


        fun addValueCommand(mainLocation: ObjectLocation, headerLabel: HeaderLabel): NotationCommand {
            return InsertMapEntryInAttributeCommand(
                mainLocation,
                valuesAttributePath,
                PositionRelation.afterLast,
                AttributeSegment.ofKey(headerLabel.asString()),
                ListAttributeNotation.empty,
                true)
        }


        fun removeValueCommand(mainLocation: ObjectLocation, headerLabel: HeaderLabel): NotationCommand {
            return RemoveInAttributeCommand(
                mainLocation,
                valuePath(headerLabel),
                false)
        }


        fun addValueTypeCommand(
            mainLocation: ObjectLocation, headerLabel: HeaderLabel, valueType: PivotValueType
        ): NotationCommand {
            return InsertListItemInAttributeCommand(
                mainLocation,
                valuePath(headerLabel),
                PositionRelation.afterLast,
                ScalarAttributeNotation(valueType.name))
        }


        fun removeValueTypeCommand(
            mainLocation: ObjectLocation, headerLabel: HeaderLabel, valueType: PivotValueType
        ): NotationCommand {
            return RemoveListItemInAttributeCommand(
                mainLocation,
                valuePath(headerLabel),
                ScalarAttributeNotation(valueType.name),
                false)
        }


        private fun valuePath(headerLabel: HeaderLabel): AttributePath {
            return valuesAttributePath.nest(AttributeSegment.ofKey(headerLabel.asString()))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Defines a standalone `pivot` attribute (a map with `rows` + `values` sub-attributes) directly into a
    // PivotSpec, so the Job PivotWorker can carry its pivot config without the surrounding AnalysisSpec (type /
    // flat) the Report document uses. Mirrors FilterSpec.Definer; the actual parse is the shared [ofNotation].
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == pivotAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, pivotAttributeName) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$pivotAttributeName' attribute notation not found: $objectLocation - $attributeName")

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(ofNotation(attributeNotation)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return rows.values.isEmpty() &&
                values.isEmpty()
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(rows)
        sink.addDigestible(values)
    }
}