package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.FormatMaterializationActionResult
import tech.kzen.auto.common.data.format.FormatMaterializationResult
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentMap


object SourceLocalFormatMaterializer {
    private val authoredAttributePath = AttributePath.ofName(AttributeName("authored"))

    fun prepare(
        graph: GraphNotation,
        source: ObjectLocation,
        fileLocation: String,
        materialized: FormatMaterializationResult
    ): FormatMaterializationActionResult {
        val schemaLocation = materialized.schemaBody?.let {
            reuseOrAllocate(graph, source, sourceLocalName(source, fileLocation, "schema"), it)
        }
        val formatBody = if (schemaLocation == null) {
            materialized.formatBody
        }
        else {
            MapAttributeNotation(materialized.formatBody.map.put(
                AttributeSegment.ofKey(requireNotNull(materialized.schemaReferenceAttribute)),
                ScalarAttributeNotation(schemaLocation.asString())))
        }
        val baseName = sourceLocalName(source, fileLocation, "format")
        val formatLocation = reuseOrAllocate(graph, source, baseName, formatBody)
        return FormatMaterializationActionResult(
            formatLocation.asString(),
            notationExecutionValue(formatBody) as MapExecutionValue,
            schemaLocation?.asString(),
            materialized.schemaBody?.let { notationExecutionValue(it) as MapExecutionValue },
            materialized.editor,
            materialized.encoding)
    }


    private fun reuseOrAllocate(
        graph: GraphNotation,
        source: ObjectLocation,
        baseName: String,
        body: MapAttributeNotation
    ): ObjectLocation {
        val document = graph.documents.map[source.documentPath]
            ?: throw IllegalArgumentException("Source document is unavailable: ${source.documentPath}")
        var suffix = 1
        while (true) {
            val name = if (suffix == 1) baseName else "$baseName $suffix"
            val location = ObjectLocation(
                source.documentPath,
                source.objectPath.nest(authoredAttributePath, ObjectName(name)))
            val existing = document.objects.notations.map[location.objectPath]
            if (existing == null) {
                return location
            }
            if (bodyOf(existing) == body) {
                return location
            }
            suffix++
        }
    }


    private fun sourceLocalName(source: ObjectLocation, fileLocation: String, kind: String): String {
        val fileName = fileLocation.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val raw = "${source.objectPath.name.value} $fileName $kind"
        return raw.replace(Regex("[^A-Za-z0-9 _.-]"), " ")
            .replace(Regex(" +"), " ")
            .trim()
    }


    private fun bodyOf(notation: ObjectNotation): MapAttributeNotation = MapAttributeNotation(
        notation.attributes.map.entries.associate { (name, value) ->
            AttributeSegment.ofKey(name.value) to value
        }.toPersistentMap())


    private fun notationExecutionValue(notation: AttributeNotation): ExecutionValue = when (notation) {
        is ScalarAttributeNotation -> TextExecutionValue(notation.value)
        is ListAttributeNotation -> ListExecutionValue(notation.values.map(::notationExecutionValue))
        is MapAttributeNotation -> MapExecutionValue(notation.map.entries.associate { (segment, value) ->
            segment.asKey() to notationExecutionValue(value)
        })
    }
}
