package tech.kzen.auto.client.objects.document.common.file

import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class FileResolutionPresentationTest {
    private val ref = DataRef(DataSourceId("file"), "orders.csv")

    @Test
    fun automaticResolutionShowsFormatBasisAndEncoding() {
        val presentation = presentation(
            FormatSelectionKind.Automatic,
            FormatResolutionBasis.Extension,
            warning = null)

        assertEquals(FileResolutionPresentation.Status.Resolved, presentation.status)
        assertEquals("Automatic → CSV", presentation.summary)
        assertEquals("file extension", presentation.basis)
        assertEquals("UTF-8", presentation.encoding)
        assertNull(presentation.error)
    }

    @Test
    fun warningAndFailureRemainDistinctRowStates() {
        val warning = presentation(
            FormatSelectionKind.Automatic,
            FormatResolutionBasis.Content,
            warning = "The file uses a regional delimiter.")
        val failure = FileResolutionPresentation.of(
            false,
            null,
            "Choose a format or encoding.")

        assertEquals(FileResolutionPresentation.Status.Warning, warning.status)
        assertEquals("The file uses a regional delimiter.", warning.warning)
        assertEquals(FileResolutionPresentation.Status.Failure, failure.status)
        assertEquals("Choose a format or encoding.", failure.summary)
        assertEquals("Choose a format or encoding.", failure.error)
    }

    @Test
    fun explicitResolutionDoesNotClaimAutomaticDetection() {
        val presentation = presentation(
            FormatSelectionKind.Explicit,
            FormatResolutionBasis.Override,
            warning = null)

        assertEquals("Explicit format → CSV", presentation.summary)
        assertEquals("explicit format", presentation.basis)
    }

    @Test
    fun lockedResolutionStatesTheStrongerGuarantee() {
        val detail = FormatResolutionDetail(
            ref,
            "configured.yaml#Csv",
            "CSV",
            FormatSelectionKind.Explicit,
            FormatResolutionBasis.Override,
            "The file uses a locked source-local format.",
            resolvedEncoding = "UTF-8",
            columnsLocked = true)

        assertEquals(
            "Columns locked → CSV",
            FileResolutionPresentation.of(false, detail, null).summary)
    }

    private fun presentation(
        selection: FormatSelectionKind,
        basis: FormatResolutionBasis,
        warning: String?
    ): FileResolutionPresentation {
        val detail = FormatResolutionDetail(
            ref,
            "configured.yaml#Csv",
            "CSV",
            selection,
            basis,
            "The file matches the configured format.",
            warning,
            resolvedEncoding = "UTF-8")
        return FileResolutionPresentation.of(false, detail, null)
    }
}
