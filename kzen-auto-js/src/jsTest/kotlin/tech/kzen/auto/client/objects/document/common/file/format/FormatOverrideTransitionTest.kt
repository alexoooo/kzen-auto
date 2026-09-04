package tech.kzen.auto.client.objects.document.common.file.format

import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.file.FileSelectionSpec
import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.format.FormatMaterializationActionResult
import tech.kzen.auto.common.data.format.FormatOverrideEditorMetadata
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.SetDocumentObjectsCommand
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class FormatOverrideTransitionTest {
    private val documentPath = DocumentPath.parse("job.yaml")
    private val source = ObjectLocation(documentPath, ObjectPath.parse("Input"))
    private val selectionAttribute = AttributeName("files")
    private val first = FileSelectionEntry(DataLocation.of("./first.csv"), null, null)
    private val second = FileSelectionEntry(DataLocation.of("./second.csv"), null, null)
    private val formatLocation = ObjectLocation(
        documentPath,
        source.objectPath.nest(AttributePath.ofName(AttributeName("formats")), ObjectName("FirstCsv")))
    private val schemaLocation = ObjectLocation(
        documentPath,
        source.objectPath.nest(AttributePath.ofName(AttributeName("schemas")), ObjectName("FirstColumns")))

    @Test
    fun applyIsOneAtomicCommandAndChangesOnlyTheTargetRow() {
        val commands = mutableListOf<NotationCommand>()
        commands += FormatOverrideTransition.command(graph(), editorState(), selectionAttribute, result())

        assertEquals(1, commands.size)
        val command = commands.single() as SetDocumentObjectsCommand
        val sourceBody = assertNotNull(command.documentObjectNotation.notations[source.objectPath])
        val entries = FileSelectionSpec.ofNotation(
            sourceBody.get(selectionAttribute) as tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
        ).entries
        assertEquals(formatLocation.asString(), entries[0].format?.asString())
        assertEquals("UTF-16LE", entries[0].encoding?.asString())
        assertEquals(second, entries[1])
        assertNotNull(command.documentObjectNotation.notations[formatLocation.objectPath])
    }

    @Test
    fun previewAndControlChangesIssueNoNotationCommand() {
        val commands = mutableListOf<NotationCommand>()
        val preview = DelimitedFormatOverrideDraft(",", true, "UTF-8", "1", "#")
            .copy(delimiter = "|", firstRowHeader = false)
            .headerExplanation()

        assertTrue(preview.contains("remains data"))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun lockColumnsInstallsFormatAndSchemaInTheSameDocumentReplacement() {
        val materialized = result(withSchema = true)
        val command = FormatOverrideTransition.command(
            graph(), editorState(), selectionAttribute, materialized)

        assertNotNull(command.documentObjectNotation.notations[formatLocation.objectPath])
        assertNotNull(command.documentObjectNotation.notations[schemaLocation.objectPath])
        assertEquals(
            graph().documents[documentPath]!!.objects.notations.map.size + 2,
            command.documentObjectNotation.notations.map.size)
    }

    private fun graph(): GraphNotation {
        val sourceBody = ObjectNotation(AttributeNameMap(mapOf(
            selectionAttribute to FileSelectionSpec(listOf(first, second)).asNotation()
        ).toPersistentMap()))
        val document = DocumentNotation(
            DocumentObjectNotation(ObjectPathMap(mapOf(
                source.objectPath to sourceBody
            ).toPersistentMap())),
            null)
        return GraphNotation(DocumentPathMap(mapOf(documentPath to document).toPersistentMap()))
    }

    private fun editorState(): FormatOverrideEditorState {
        val part = DataPart(
            DataRole.main,
            DataRef.ofLocation(first.location),
            null,
            ResolvedReadSpec(
                ReaderCapabilityIdentity("kzen", "delimited", "1"),
                emptyList(),
                MapExecutionValue(emptyMap())))
        val detail = FormatResolutionDetail(
            part.ref,
            "formats.yaml#Delimited",
            "Delimited",
            FormatSelectionKind.Automatic,
            FormatResolutionBasis.Content,
            "Detected from the sample.")
        return FormatOverrideEditorState(
            source,
            0,
            first,
            part,
            detail,
            ConfiguredFormatDetail(
                "formats.yaml#Delimited",
                "Delimited",
                listOf("csv"),
                "delimited-authoring",
                "auto-js/datasource/delimited-format-override-editor.yaml#DelimitedFormatOverrideEditor",
                true),
            listOf("UTF-8", "UTF-16LE"))
    }

    private fun result(withSchema: Boolean = false): FormatMaterializationActionResult =
        FormatMaterializationActionResult(
            formatLocation.asString(),
            MapExecutionValue(mapOf(
                "is" to TextExecutionValue("ConfiguredDelimitedFormat"),
                "delimiter" to TextExecutionValue("|"))),
            schemaLocation.asString().takeIf { withSchema },
            MapExecutionValue(mapOf(
                "is" to TextExecutionValue("RecordSchema"),
                "fields" to MapExecutionValue(emptyMap())))
                .takeIf { withSchema },
            FormatOverrideEditorMetadata(
                "auto-js/datasource/delimited-format-override-editor.yaml#DelimitedFormatOverrideEditor",
                "Delimited"),
            "UTF-16LE")
}
