package tech.kzen.auto.client.objects.document.common.file.format

import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.lib.common.exec.data.shape.DataShapeResult
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class FormatRepeatabilityPresentationTest {
    @Test
    fun authorableAutomaticRecordOffersBothGuarantees() {
        val presentation = presentation(
            format(),
            shapeResult = DataShapeResult.Observed(
                LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("id", "amount")))))

        assertTrue(presentation.makeExplicit.enabled)
        assertTrue(presentation.lockColumns.enabled)
        assertTrue(presentation.makeExplicit.explanation.contains("columns may still change"))
        assertTrue(presentation.lockColumns.explanation.contains("width"))
        assertNull(presentation.inspection)
    }

    @Test
    fun missingAndLoadingShapeRequireBoundedInspection() {
        val missing = presentation(format())
        assertFalse(missing.lockColumns.enabled)
        assertEquals("Inspect columns", missing.inspection?.label)
        assertTrue(missing.inspection?.enabled == true)

        val loading = presentation(format(), shapeInspecting = true)
        assertFalse(loading.lockColumns.enabled)
        assertEquals("Column inspection is in progress.", loading.lockColumns.explanation)
        assertEquals("Inspecting columns…", loading.inspection?.label)
        assertFalse(loading.inspection?.enabled ?: true)
    }

    @Test
    fun failedUnavailableAndNonRecordShapesExplainWhyLockIsDisabled() {
        val failed = presentation(format(), shapeError = "sample changed")
        assertEquals(
            "Column inspection failed: sample changed",
            failed.lockColumns.explanation)

        val unavailable = presentation(format(), shapeResult = DataShapeResult.Unavailable)
        assertTrue(unavailable.lockColumns.explanation.contains("did not produce a record contract"))

        val scalar = presentation(
            format(),
            shapeResult = DataShapeResult.Observed(
                LegacyDataShapeBridge.payload(TypeMetadata.string)))
        assertEquals(
            "Only record-shaped data has columns that can be locked.",
            scalar.lockColumns.explanation)
    }

    @Test
    fun unrepresentableRecordAndUnsupportedCatalogCapabilitiesStayDisabled() {
        val duplicateColumns = presentation(
            format(),
            shapeResult = DataShapeResult.Observed(
                LegacyDataShapeBridge.tabular(HeaderListing.of(listOf("value", "value")))))
        assertEquals(
            "The observed record contains columns that cannot be authored as a schema.",
            duplicateColumns.lockColumns.explanation)

        val noAuthoring = presentation(format(authoring = false))
        assertFalse(noAuthoring.makeExplicit.enabled)
        assertFalse(noAuthoring.lockColumns.enabled)
        assertTrue(noAuthoring.makeExplicit.explanation.contains("cannot be saved"))

        val noLocking = presentation(format(locking = false))
        assertTrue(noLocking.makeExplicit.enabled)
        assertFalse(noLocking.lockColumns.enabled)
        assertEquals("Delimited cannot lock observed columns.", noLocking.lockColumns.explanation)
    }

    @Test
    fun explicitAndLockedRowsStateTheirDifferentGuarantees() {
        val explicit = presentation(format(), resolution(selection = FormatSelectionKind.Explicit))
        assertEquals(
            "Explicit format: reader settings stay fixed, but columns may still change.",
            explicit.currentGuarantee)

        val locked = presentation(
            format(),
            resolution(selection = FormatSelectionKind.Explicit, columnsLocked = true))
        assertEquals(
            "Columns locked: header, width, and observed types must continue to match.",
            locked.currentGuarantee)
    }

    private fun presentation(
        format: ConfiguredFormatDetail?,
        resolution: FormatResolutionDetail = resolution(),
        shapeInspecting: Boolean = false,
        shapeResult: DataShapeResult? = null,
        shapeError: String? = null
    ): FormatRepeatabilityPresentation = FormatRepeatabilityPresentation.of(
        resolution, format, shapeInspecting, shapeResult, shapeError)

    private fun format(
        authoring: Boolean = true,
        locking: Boolean = true
    ): ConfiguredFormatDetail = ConfiguredFormatDetail(
        "formats.yaml#Delimited",
        "Delimited",
        listOf("csv"),
        "delimited-authoring",
        null,
        authoring,
        locking)

    private fun resolution(
        selection: FormatSelectionKind = FormatSelectionKind.Automatic,
        columnsLocked: Boolean = false
    ): FormatResolutionDetail = FormatResolutionDetail(
        DataRef(null, "orders.csv"),
        "formats.yaml#Delimited",
        "Delimited",
        selection,
        if (selection == FormatSelectionKind.Automatic) {
            FormatResolutionBasis.Content
        }
        else {
            FormatResolutionBasis.Override
        },
        "Resolved for this file.",
        columnsLocked = columnsLocked)
}
