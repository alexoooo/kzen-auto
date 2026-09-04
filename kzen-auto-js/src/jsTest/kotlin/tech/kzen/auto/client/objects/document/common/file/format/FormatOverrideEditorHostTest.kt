package tech.kzen.auto.client.objects.document.common.file.format

import react.ChildrenBuilder
import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame


class FormatOverrideEditorHostTest {
    private class TestEditor(
        objectLocation: ObjectLocation
    ): FormatOverrideEditor(objectLocation) {
        override fun ChildrenBuilder.child(block: FormatOverrideEditorProps.() -> Unit) {}
    }

    private val formatReference = "formats.yaml#ContributedFormat"
    private val editorReference = "auto-js/test.yaml#ContributedFormatEditor"
    private val resolution = FormatResolutionDetail(
        DataRef(DataSourceId("file"), "orders.test"),
        formatReference,
        "Contributed format",
        FormatSelectionKind.Automatic,
        FormatResolutionBasis.Content,
        "The contributed probe matched the file.")

    @Test
    fun contributedMarkerSelectsItsEditorWithoutHostChanges() {
        val editor = TestEditor(ObjectLocation.parse(editorReference))
        val catalog = catalog(authoringAvailable = true, editorReference = editorReference)

        val selected = assertIs<FormatOverrideEditorHost.Selection.Available>(
            FormatOverrideEditorHost.selection(catalog, resolution, listOf(editor)))

        assertSame(editor, selected.editor)
        assertEquals(formatReference, selected.format.reference)
    }

    @Test
    fun formatWithoutAuthoringExplainsWhyQuickControlsAreAbsent() {
        val selected = assertIs<FormatOverrideEditorHost.Selection.Unavailable>(
            FormatOverrideEditorHost.selection(
                catalog(authoringAvailable = false, editorReference = null),
                resolution,
                emptyList()))

        assertEquals(
            "Contributed format does not provide file-specific quick controls.",
            selected.explanation)
    }

    @Test
    fun missingClientEditorDoesNotExposeItsInternalReference() {
        val selected = assertIs<FormatOverrideEditorHost.Selection.Unavailable>(
            FormatOverrideEditorHost.selection(
                catalog(authoringAvailable = true, editorReference = editorReference),
                resolution,
                emptyList()))

        assertEquals(
            "Quick controls for Contributed format are not installed in this client.",
            selected.explanation)
        assertFalse(selected.explanation.contains(editorReference))
    }

    private fun catalog(
        authoringAvailable: Boolean,
        editorReference: String?
    ): FileFormatCatalog = FileFormatCatalog(
        listOf(ConfiguredFormatDetail(
            formatReference,
            "Contributed format",
            listOf("test"),
            authoringCapabilityIdentity = "test-authoring",
            overrideEditorReference = editorReference,
            authoringAvailable = authoringAvailable)),
        listOf("UTF-8"))
}
