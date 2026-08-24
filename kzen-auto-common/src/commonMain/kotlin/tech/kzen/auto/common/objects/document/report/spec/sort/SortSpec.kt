package tech.kzen.auto.common.objects.document.report.spec.sort

import tech.kzen.auto.common.data.schema.HeaderLabel
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
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertMapEntryInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


/**
 * The multi-key sort configuration for the Job [tech.kzen.auto.server.objects.job.worker.SortWorker]: an ORDERED
 * list of sort keys, each a [column][SortColumnSpec.column] plus a direction. The list order IS the sort
 * PRIORITY — the first key is primary, ties broken by the next, and so on.
 *
 * Report has no sort stage, so — unlike [tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec]
 * / [tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec] — this spec is Job-native (no
 * Report-document nesting; the SortWorker carries it as a top-level `sort` attribute, defined by [Definer]).
 *
 * Notation shape — an ordered map `column-label -> ascending-boolean` (so a column appears at most once), e.g.
 *
 *     sort:
 *       "0|city": true
 *       "0|amount": false
 *
 * The MAP's insertion order IS the key priority: [Definer] reads it verbatim via `firstAttribute` (NOT
 * `mergeAttribute`, which could reorder across the inheritance chain) so the authored order survives exactly.
 */
data class SortSpec(
    val columns: List<SortColumnSpec>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = SortSpec(listOf())

        val sortAttributeName = AttributeName("sort")

        val sortAttributePath = AttributePath.ofName(sortAttributeName)


        fun ofNotation(attributeNotation: MapAttributeNotation): SortSpec {
            val columns = attributeNotation.map.entries.map { (key, value) ->
                SortColumnSpec(
                    HeaderLabel.ofString(key.asKey()),
                    value.asBoolean() ?: true)
            }
            return SortSpec(columns)
        }


        //-------------------------------------------------------------------------------------------------------------
        // Canonical command builders for the Job SortSpecEditor — mutate the ordered `sort` map that [ofNotation]
        // reads, so the editor applies these instead of hand-rolling notation commands (mirrors FilterSpec /
        // FormulaSpec). The map KEY is the column's encoded label ([HeaderLabel.asString]) so it round-trips
        // through [ofNotation]; the VALUE is the direction as its "true"/"false" string (what [asBoolean] parses).

        // Appends a new sort key for [column] at LOWEST priority (the map's tail — insertion order IS priority),
        // ascending by default; createAncestorsIfAbsent materializes the `sort` map when this is the first key.
        fun addCommand(mainLocation: ObjectLocation, column: HeaderLabel): NotationCommand {
            return InsertMapEntryInAttributeCommand(
                mainLocation,
                sortAttributePath,
                PositionRelation.afterLast,
                AttributeSegment.ofKey(column.asString()),
                ScalarAttributeNotation(true.toString()),
                true)
        }


        // Removes [column]'s sort key; removeContainerIfEmpty drops the now-empty `sort` map so the notation
        // matches SortSpec.empty rather than leaving an empty map behind.
        fun removeCommand(mainLocation: ObjectLocation, column: HeaderLabel): NotationCommand {
            return RemoveInAttributeCommand(
                mainLocation,
                sortColumnPath(column),
                true)
        }


        // Sets [column]'s direction (true = ascending) in place, preserving its priority position.
        fun updateAscendingCommand(
            mainLocation: ObjectLocation, column: HeaderLabel, ascending: Boolean
        ): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                sortColumnPath(column),
                ScalarAttributeNotation(ascending.toString()))
        }


        private fun sortColumnPath(column: HeaderLabel): AttributePath {
            return sortAttributePath.nest(AttributeSegment.ofKey(column.asString()))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Defines a standalone `sort` attribute (an ordered column -> ascending map) directly into a SortSpec, so the
    // Job SortWorker can carry its sort config as a top-level value attribute. Mirrors FilterSpec.Definer, but
    // uses firstAttribute (not mergeAttribute) because the map's ORDER is the sort priority and must be preserved.
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == sortAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, sortAttributeName) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$sortAttributeName' attribute notation not found: $objectLocation - $attributeName")

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(ofNotation(attributeNotation)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return columns.isEmpty()
    }


    override fun digest(sink: Digest.Sink) {
        // Ordered: the sort-key priority is significant.
        sink.addDigestibleList(columns)
    }
}


/**
 * One sort key: the [column] to order by and whether it sorts [ascending]. See [SortSpec].
 */
data class SortColumnSpec(
    val column: HeaderLabel,
    val ascending: Boolean
):
    Digestible
{
    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(column)
        sink.addBoolean(ascending)
    }
}
