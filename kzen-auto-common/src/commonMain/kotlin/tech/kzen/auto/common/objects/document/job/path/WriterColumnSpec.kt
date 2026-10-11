package tech.kzen.auto.common.objects.document.job.path

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
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.platform.collect.toPersistentMap


/**
 * How a writer flattens each value into a row: an ordered list of columns. `*` is every top-level column of
 * the payload; any other item is a path entry in [PathProjectionSpec]'s notation (a path string or
 * `{path, as}`), so a column can be a metadata field (`meta.name`, or a bare name only the metadata has) or a
 * nested scalar (`instrument.symbol`). A writer column never unnests: a path with `[*]` is an error (flatten a
 * list with a Paths Worker upstream). No columns is the same as `[*]`.
 *
 * `*` can also be a map that narrows and renames the payload's columns by name,
 * `{path: "*", allow: [regex, …], exclude: [regex, …], as: {c1: value}}`: see [ColumnPatterns].
 */
data class WriterColumnSpec(
    val columns: List<WriterColumn>
): Digestible {
    companion object {
        const val allPayloadFields = "*"

        val payloadFields = WriterColumnSpec(listOf())

        private const val allowKey = "allow"
        private const val excludeKey = "exclude"

        private val pathSegment = AttributeSegment.ofKey(PathProjectionSpec.pathKey)
        private val allowSegment = AttributeSegment.ofKey(allowKey)
        private val excludeSegment = AttributeSegment.ofKey(excludeKey)
        private val aliasSegment = AttributeSegment.ofKey(PathProjectionSpec.aliasKey)


        fun ofNotation(attributeNotation: ListAttributeNotation): WriterColumnSpec =
            WriterColumnSpec(attributeNotation.values.map { item ->
                payloadFieldsOfNotation(item)
                    ?: WriterColumn.Path(PathProjectionSpec.entryOfNotation(item))
            })


        // `*` or `{path: "*", …}`; null for any other item
        private fun payloadFieldsOfNotation(item: AttributeNotation): WriterColumn.PayloadFields? {
            if (item is ScalarAttributeNotation) {
                return if (item.asString().trim() == allPayloadFields) WriterColumn.PayloadFields.every else null
            }
            val map = (item as? MapAttributeNotation)?.map
                ?: return null
            if ((map[pathSegment] as? ScalarAttributeNotation)?.asString()?.trim() != allPayloadFields) {
                return null
            }
            val renames = when (val alias = map[aliasSegment]) {
                null -> mapOf()
                is MapAttributeNotation -> alias.map.entries.associate { (name, value) ->
                    val text = (value as? ScalarAttributeNotation)?.asString()
                        ?: throw IllegalArgumentException("'$allPayloadFields' rename of '${name.asString()}' must be a name")
                    name.asString() to text
                }
                else -> throw IllegalArgumentException("'$allPayloadFields' renames must be a map of column to name")
            }
            return WriterColumn.PayloadFields(
                ColumnPatterns(patternsOf(map[allowSegment], allowKey), patternsOf(map[excludeSegment], excludeKey)),
                renames.filterValues { it.isNotBlank() })
        }


        private fun patternsOf(notation: AttributeNotation?, key: String): List<String> =
            when (notation) {
                null -> listOf()
                is ListAttributeNotation -> notation.values.map {
                    (it as? ScalarAttributeNotation)?.asString()
                        ?: throw IllegalArgumentException("'$allPayloadFields' $key must be a list of patterns")
                }
                else -> throw IllegalArgumentException("'$allPayloadFields' $key must be a list of patterns")
            }


        /** `*` as notation: the bare scalar unless it narrows or renames. */
        fun payloadFieldsNotation(column: WriterColumn.PayloadFields): AttributeNotation {
            if (column.isEvery()) {
                return ScalarAttributeNotation(allPayloadFields)
            }
            val map = LinkedHashMap<AttributeSegment, AttributeNotation>()
            map[pathSegment] = ScalarAttributeNotation(allPayloadFields)
            if (column.patterns.allow.isNotEmpty()) {
                map[allowSegment] = ListAttributeNotation(
                    column.patterns.allow.map { ScalarAttributeNotation(it) }.toPersistentList())
            }
            if (column.patterns.exclude.isNotEmpty()) {
                map[excludeSegment] = ListAttributeNotation(
                    column.patterns.exclude.map { ScalarAttributeNotation(it) }.toPersistentList())
            }
            if (column.renames.isNotEmpty()) {
                map[aliasSegment] = MapAttributeNotation(column.renames.entries.associate { (name, alias) ->
                    AttributeSegment.ofKey(name) to ScalarAttributeNotation(alias)
                }.toPersistentMap())
            }
            return MapAttributeNotation(map.toPersistentMap())
        }


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


        /**
         * Replaces the `*` at [index] with [column], its patterns or renames edited. No columns is an implicit `*`
         * (an [index] of -1), so its first edit writes the list.
         */
        fun payloadFieldsCommand(
            mainLocation: ObjectLocation,
            attributeName: AttributeName,
            index: Int,
            column: WriterColumn.PayloadFields
        ): NotationCommand {
            if (index < 0) {
                return UpsertAttributeCommand(
                    mainLocation,
                    attributeName,
                    ListAttributeNotation(persistentListOf(payloadFieldsNotation(column))))
            }
            return UpdateInAttributeCommand(
                mainLocation,
                AttributePath.ofName(attributeName).nest(AttributeSegment.ofIndex(index)),
                payloadFieldsNotation(column))
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
        /** The payload's columns that [patterns] keep, in payload order, each renamed per [renames] (by name). */
        data class PayloadFields(
            val patterns: ColumnPatterns = ColumnPatterns.none,
            val renames: Map<String, String> = mapOf()
        ): WriterColumn {
            companion object {
                val every = PayloadFields()
            }


            fun isEvery(): Boolean =
                patterns.isEmpty() && renames.isEmpty()


            fun outputName(name: String): String =
                renames[name] ?: name


            fun withPatterns(patterns: ColumnPatterns): PayloadFields =
                PayloadFields(patterns, renames)


            fun withRename(name: String, alias: String): PayloadFields =
                PayloadFields(patterns, if (alias.isBlank()) renames - name else renames + (name to alias))


            override fun digest(sink: Digest.Sink) {
                sink.addUtf8(allPayloadFields)
                sink.addInt(patterns.allow.size)
                patterns.allow.forEach { sink.addUtf8(it) }
                sink.addInt(patterns.exclude.size)
                patterns.exclude.forEach { sink.addUtf8(it) }
                sink.addInt(renames.size)
                for ((name, alias) in renames) {
                    sink.addUtf8(name)
                    sink.addUtf8(alias)
                }
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


    /** Whether any column reads the payload's: no columns is every payload column. */
    fun readsPayload(): Boolean =
        columns.isEmpty() || columns.any { it is WriterColumn.PayloadFields }


    /** Every payload column as it is: no columns, or only `*`s that neither narrow nor rename. */
    fun isPayloadFieldsOnly(): Boolean =
        columns.all { it is WriterColumn.PayloadFields && it.isEvery() }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleList(columns)
    }
}
