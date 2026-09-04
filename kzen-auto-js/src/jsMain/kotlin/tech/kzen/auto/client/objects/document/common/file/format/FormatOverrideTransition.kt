package tech.kzen.auto.client.objects.document.common.file.format

import tech.kzen.auto.common.data.file.FileSelectionSpec
import tech.kzen.auto.common.data.format.FormatMaterializationActionResult
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BinaryHandleExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.PositionedObjectPath
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.SetDocumentObjectsCommand
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.platform.collect.toPersistentMap


object FormatOverrideTransition {
    fun command(
        graphNotation: GraphNotation,
        editorState: FormatOverrideEditorState,
        selectionAttribute: AttributeName,
        materialized: FormatMaterializationActionResult
    ): SetDocumentObjectsCommand {
        val source = editorState.source
        val document = requireNotNull(graphNotation.documents[source.documentPath]) {
            "Source document is no longer available"
        }
        val sourceBody = requireNotNull(document.objects.notations[source.objectPath]) {
            "Source is no longer available"
        }
        val selection = graphNotation.firstAttribute(source, selectionAttribute) as? ListAttributeNotation
            ?: throw IllegalStateException("File selection is no longer available")
        val entries = FileSelectionSpec.ofNotation(selection).entries
        require(editorState.rowIndex in entries.indices &&
                entries[editorState.rowIndex] == editorState.entry) {
            "The selected file changed while quick controls were open"
        }

        var objects = document.objects
        objects = install(objects, source, materialized.formatReference, materialized.formatBody)
        val schemaReference = materialized.schemaReference
        if (schemaReference != null) {
            objects = install(
                objects,
                source,
                schemaReference,
                requireNotNull(materialized.schemaBody))
        }

        val updatedEntries = entries.toMutableList()
        updatedEntries[editorState.rowIndex] = editorState.entry.copy(
            format = CommonPluginCoordinate.ofString(materialized.formatReference),
            encoding = materialized.encoding?.let(CommonDataEncodingSpec::ofString))
        val updatedSource = sourceBody.upsertAttribute(
            selectionAttribute,
            FileSelectionSpec(updatedEntries).asNotation())
        objects = objects.withModifiedObject(source.objectPath, updatedSource)
        return SetDocumentObjectsCommand(source.documentPath, objects)
    }

    private fun install(
        objects: DocumentObjectNotation,
        source: ObjectLocation,
        reference: String,
        body: MapExecutionValue
    ): DocumentObjectNotation {
        val location = ObjectLocation.parse(reference)
        require(location.documentPath == source.documentPath &&
                location.objectPath.startsWith(source.objectPath)) {
            "Materialized objects must be local to the source"
        }
        val notation = objectNotation(body)
        val existing = objects.notations[location.objectPath]
        if (existing != null) {
            require(existing == notation) {
                "Materialized object already exists with different content: $location"
            }
            return objects
        }
        return objects.withNewObject(
            PositionedObjectPath(location.objectPath, PositionIndex(objects.notations.map.size)),
            notation)
    }

    internal fun objectNotation(value: MapExecutionValue): ObjectNotation =
        ObjectNotation(AttributeNameMap(value.values.map { (key, child) ->
            AttributeName.parse(key) to attributeNotation(child)
        }.toMap().toPersistentMap()))

    private fun attributeNotation(value: ExecutionValue): AttributeNotation = when (value) {
        is TextExecutionValue -> ScalarAttributeNotation(value.value)
        is BooleanExecutionValue -> ScalarAttributeNotation(value.value.toString())
        is NumberExecutionValue -> ScalarAttributeNotation(value.value.toString())
        is LongExecutionValue -> ScalarAttributeNotation(value.value.toString())
        is ListExecutionValue -> ListAttributeNotation(
            value.values.map(::attributeNotation).toPersistentList())
        is MapExecutionValue -> MapAttributeNotation(value.values.map { (key, child) ->
            AttributeSegment.parse(key) to attributeNotation(child)
        }.toMap().toPersistentMap())
        NullExecutionValue -> throw IllegalArgumentException(
            "Notation bodies cannot contain null execution values")
        is BinaryExecutionValue, is BinaryHandleExecutionValue -> throw IllegalArgumentException(
            "Notation bodies cannot contain binary execution values")
    }
}
