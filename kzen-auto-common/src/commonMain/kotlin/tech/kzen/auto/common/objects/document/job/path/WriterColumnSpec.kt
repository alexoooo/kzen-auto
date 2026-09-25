package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.persistentListOf


/**
 * How a writer flattens each value into a row: an ordered list of columns. `*` is every top-level column of
 * the payload; any other item is a path entry in [PathProjectionSpec]'s notation (a path string or
 * `{path, as}`), so a column can be a metadata field (`meta.name`, or a bare name only the metadata has) or a
 * nested scalar (`instrument.symbol`). A writer column never unnests: a path with `[*]` is an error (flatten a
 * list with a Paths Worker upstream). No columns is the same as `[*]`.
 */
data class WriterColumnSpec(
    val columns: List<WriterColumn>
): Digestible {
    companion object {
        const val allPayloadFields = "*"

        val payloadFields = WriterColumnSpec(listOf())


        fun ofNotation(attributeNotation: ListAttributeNotation): WriterColumnSpec =
            WriterColumnSpec(attributeNotation.values.map { item ->
                if (item is ScalarAttributeNotation && item.asString().trim() == allPayloadFields) {
                    WriterColumn.PayloadFields
                }
                else {
                    WriterColumn.Path(PathProjectionSpec.entryOfNotation(item))
                }
            })


        //-------------------------------------------------------------------------------------------------------------
        // Command builders for the Job WriterColumnsEditor over the writer's [attributeName] list. Removing and
        // aliasing a path column are PathProjectionSpec's commands on this list.

        /**
         * Adds a path column. No columns means every payload column, so the first path added writes `*` before it:
         * adding a column never drops the payload's.
         */
        fun addPathCommand(
            mainLocation: ObjectLocation,
            attributeName: AttributeName,
            current: WriterColumnSpec,
            path: ProjectionPath
        ): NotationCommand {
            if (current.columns.isNotEmpty()) {
                return PathProjectionSpec.addCommand(mainLocation, path, AttributePath.ofName(attributeName))
            }
            return UpsertAttributeCommand(
                mainLocation,
                attributeName,
                ListAttributeNotation(persistentListOf(
                    ScalarAttributeNotation(allPayloadFields),
                    PathProjectionSpec.entryNotation(path, null))))
        }


        /** Adds `*`, every payload column, at the end. */
        fun addPayloadFieldsCommand(mainLocation: ObjectLocation, attributeName: AttributeName): NotationCommand {
            return InsertListItemInAttributeCommand(
                mainLocation,
                AttributePath.ofName(attributeName),
                PositionRelation.afterLast,
                ScalarAttributeNotation(allPayloadFields))
        }
    }


    /** One column (or, for [PayloadFields], a run of columns). */
    sealed interface WriterColumn: Digestible {
        data object PayloadFields: WriterColumn {
            override fun digest(sink: Digest.Sink) {
                sink.addUtf8(allPayloadFields)
            }
        }

        data class Path(val entry: PathProjectionEntry): WriterColumn {
            override fun digest(sink: Digest.Sink) {
                sink.addDigestible(entry)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Defines a writer's column-selection attribute (any name) from its list notation; a missing attribute is the
    // default, every payload column.
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
                .firstAttribute(objectLocation, AttributePath.ofName(attributeName))
                ?: return AttributeDefinitionAttempt.success(ValueAttributeDefinition(payloadFields))

            val list = attributeNotation as? ListAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "$objectLocation - $attributeName: expected a list of columns")

            return try {
                AttributeDefinitionAttempt.success(ValueAttributeDefinition(ofNotation(list)))
            }
            catch (e: IllegalArgumentException) {
                AttributeDefinitionAttempt.failure("$objectLocation - $attributeName: ${e.message}")
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The path entries, in order (without the payload-fields runs). */
    fun pathEntries(): List<PathProjectionEntry> =
        columns.filterIsInstance<WriterColumn.Path>().map { it.entry }


    fun isPayloadFieldsOnly(): Boolean =
        columns.all { it == WriterColumn.PayloadFields }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleList(columns)
    }
}
