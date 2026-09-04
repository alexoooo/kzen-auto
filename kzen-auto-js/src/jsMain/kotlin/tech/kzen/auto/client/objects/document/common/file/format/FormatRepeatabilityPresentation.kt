package tech.kzen.auto.client.objects.document.common.file.format

import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.schema.AuthoredRecordSchemaDraft
import tech.kzen.lib.common.exec.data.shape.DataShapeResult
import tech.kzen.lib.common.exec.data.type.DataType


data class FormatRepeatabilityPresentation(
    val makeExplicit: Action,
    val lockColumns: Action,
    val inspection: Inspection?,
    val currentGuarantee: String?
) {
    data class Action(
        val enabled: Boolean,
        val explanation: String
    )

    data class Inspection(
        val label: String,
        val enabled: Boolean
    )

    companion object {
        private const val makeExplicitCopy =
            "Stops format detection for this file. Reader settings stay fixed, but columns may still change."
        private const val lockColumnsCopy =
            "Saves the observed columns and rejects header, width, or type drift."

        fun of(
            resolution: FormatResolutionDetail,
            format: ConfiguredFormatDetail?,
            shapeInspecting: Boolean,
            shapeResult: DataShapeResult?,
            shapeError: String?
        ): FormatRepeatabilityPresentation {
            if (resolution.selection != FormatSelectionKind.Automatic) {
                return FormatRepeatabilityPresentation(
                    Action(false, makeExplicitCopy),
                    Action(false, lockColumnsCopy),
                    null,
                    if (resolution.columnsLocked) {
                        "Columns locked: header, width, and observed types must continue to match."
                    }
                    else {
                        "Explicit format: reader settings stay fixed, but columns may still change."
                    })
            }

            if (format == null) {
                val loading = "Format capabilities are still loading."
                return FormatRepeatabilityPresentation(
                    Action(false, loading), Action(false, loading), null, null)
            }
            if (!format.authoringAvailable) {
                val unavailable = "${format.label} cannot be saved as a file-specific explicit format."
                return FormatRepeatabilityPresentation(
                    Action(false, unavailable), Action(false, unavailable), null, null)
            }

            val makeExplicit = Action(true, makeExplicitCopy)
            if (!format.columnLockingAvailable) {
                return FormatRepeatabilityPresentation(
                    makeExplicit,
                    Action(false, "${format.label} cannot lock observed columns."),
                    null,
                    null)
            }

            val lock = when {
                shapeInspecting ->
                    Action(false, "Column inspection is in progress.")
                shapeError != null ->
                    Action(false, "Column inspection failed: $shapeError")
                shapeResult == null ->
                    Action(false, "Inspect this file before locking its columns.")
                shapeResult == DataShapeResult.Unavailable ->
                    Action(false, "Inspection did not produce a record contract that can be locked.")
                shapeResult is DataShapeResult.Observed -> {
                    val structural = shapeResult.shape.itemType.structural
                    when {
                        structural !is DataType.Record ->
                            Action(false, "Only record-shaped data has columns that can be locked.")
                        AuthoredRecordSchemaDraft.from(shapeResult.shape.itemType) == null ->
                            Action(false, "The observed record contains columns that cannot be authored as a schema.")
                        else ->
                            Action(true, lockColumnsCopy)
                    }
                }
                else ->
                    Action(false, "Inspection did not produce a record contract that can be locked.")
            }
            val inspection = when {
                shapeInspecting -> Inspection("Inspecting columns…", false)
                shapeResult == null && shapeError == null -> Inspection("Inspect columns", true)
                lock.enabled -> null
                else -> Inspection("Inspect again", true)
            }
            return FormatRepeatabilityPresentation(makeExplicit, lock, inspection, null)
        }
    }
}
