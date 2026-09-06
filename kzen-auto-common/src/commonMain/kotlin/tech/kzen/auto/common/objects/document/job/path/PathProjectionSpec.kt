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
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.persistentMapOf


/**
 * The path-projection Worker's configuration (E8 item 1): an ordered list of [PathProjectionEntry]. Notation
 * is a `paths` list whose items are either a bare path string or a map `{path: …, as: …}`; the order is the
 * output column order. Syntax is checked here; existence, scalar leaves and output-name collisions are
 * checked by [PathBinding] against the upstream contract, at validation time and again at run time.
 */
data class PathProjectionSpec(
    val entries: List<PathProjectionEntry>
): Digestible {
    companion object {
        val empty = PathProjectionSpec(listOf())

        val pathsAttributeName = AttributeName("paths")
        val pathsAttributePath = AttributePath.ofName(pathsAttributeName)

        const val pathKey = "path"
        const val aliasKey = "as"

        private val pathSegment = AttributeSegment.ofKey(pathKey)
        private val aliasSegment = AttributeSegment.ofKey(aliasKey)


        fun ofNotation(attributeNotation: ListAttributeNotation): PathProjectionSpec {
            val entries = attributeNotation.values.map { item ->
                when (item) {
                    is ScalarAttributeNotation -> PathProjectionEntry(ProjectionPath.parse(item.asString()))

                    is MapAttributeNotation -> {
                        val path = (item.map[pathSegment] as? ScalarAttributeNotation)?.asString()
                            ?: throw IllegalArgumentException("Path entry needs a '$pathKey': $item")
                        val alias = (item.map[aliasSegment] as? ScalarAttributeNotation)?.asString()
                        PathProjectionEntry(ProjectionPath.parse(path), alias?.takeIf { it.isNotBlank() })
                    }

                    else -> throw IllegalArgumentException("Path entry must be a path or a {path, as} map: $item")
                }
            }
            return PathProjectionSpec(entries)
        }


        //-------------------------------------------------------------------------------------------------------------
        // Canonical command builders for the Job PathProjectionEditor — mutate the ordered `paths` list that
        // [ofNotation] reads, so the editor applies these instead of hand-rolling notation commands (the SortSpec
        // precedent). An entry without an alias is written as a bare path string; with one, as a {path, as} map.

        fun addCommand(mainLocation: ObjectLocation, path: ProjectionPath): NotationCommand {
            return InsertListItemInAttributeCommand(
                mainLocation,
                pathsAttributePath,
                PositionRelation.afterLast,
                entryNotation(path, null))
        }


        fun removeCommand(mainLocation: ObjectLocation, index: Int): NotationCommand {
            return RemoveInAttributeCommand(
                mainLocation,
                pathsAttributePath.nest(AttributeSegment.ofIndex(index)),
                false)
        }


        fun aliasCommand(mainLocation: ObjectLocation, index: Int, path: ProjectionPath, alias: String?): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                pathsAttributePath.nest(AttributeSegment.ofIndex(index)),
                entryNotation(path, alias?.takeIf { it.isNotBlank() }))
        }


        private fun entryNotation(path: ProjectionPath, alias: String?): AttributeNotation {
            if (alias == null) {
                return ScalarAttributeNotation(path.asString())
            }
            return MapAttributeNotation(persistentMapOf(
                pathSegment to ScalarAttributeNotation(path.asString()),
                aliasSegment to ScalarAttributeNotation(alias)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Defines a standalone `paths` attribute (a list of path entries) directly into a PathProjectionSpec, so the
    // Job PathProjectionWorker carries its config as a top-level value attribute (the SortSpec.Definer pattern).
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == pathsAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, pathsAttributeName) as? ListAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$pathsAttributeName' attribute notation not found: $objectLocation - $attributeName")

            return try {
                AttributeDefinitionAttempt.success(
                    ValueAttributeDefinition(ofNotation(attributeNotation)))
            }
            catch (e: IllegalArgumentException) {
                AttributeDefinitionAttempt.failure("$objectLocation - $attributeName: ${e.message}")
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean = entries.isEmpty()


    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleList(entries)
    }
}
