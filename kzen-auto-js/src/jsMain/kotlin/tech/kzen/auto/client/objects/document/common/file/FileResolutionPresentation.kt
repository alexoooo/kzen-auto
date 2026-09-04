package tech.kzen.auto.client.objects.document.common.file

import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind


data class FileResolutionPresentation(
    val status: Status,
    val summary: String,
    val basis: String?,
    val reason: String?,
    val warning: String?,
    val encoding: String?,
    val error: String?
) {
    enum class Status {
        Loading,
        Resolved,
        Warning,
        Failure
    }

    companion object {
        val loading = FileResolutionPresentation(
            Status.Loading,
            "Detecting format…",
            null,
            null,
            null,
            null,
            null)

        fun of(
            resolving: Boolean,
            detail: FormatResolutionDetail?,
            error: String?
        ): FileResolutionPresentation {
            if (resolving) {
                return loading
            }
            error?.let {
                return FileResolutionPresentation(
                    Status.Failure,
                    "Format could not be resolved",
                    null,
                    null,
                    null,
                    null,
                    it)
            }

            val settledDetail = requireNotNull(detail) {
                "Settled file resolution has no result or error"
            }
            val summary = when (settledDetail.selection) {
                FormatSelectionKind.Automatic -> "Automatic → ${settledDetail.displayLabel}"
                FormatSelectionKind.Explicit -> if (settledDetail.columnsLocked) {
                    "Columns locked → ${settledDetail.displayLabel}"
                }
                else {
                    "Explicit format → ${settledDetail.displayLabel}"
                }
            }
            return FileResolutionPresentation(
                if (settledDetail.warning == null) Status.Resolved else Status.Warning,
                summary,
                basisLabel(settledDetail.basis),
                settledDetail.reason,
                settledDetail.warning,
                settledDetail.resolvedEncoding,
                null)
        }

        private fun basisLabel(basis: FormatResolutionBasis): String = when (basis) {
            FormatResolutionBasis.Override -> "explicit format"
            FormatResolutionBasis.Extension -> "file extension"
            FormatResolutionBasis.Content -> "file contents"
            FormatResolutionBasis.Fallback -> "text fallback"
        }
    }
}
