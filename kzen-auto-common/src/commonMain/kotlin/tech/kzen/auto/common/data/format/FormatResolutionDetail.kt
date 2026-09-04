package tech.kzen.auto.common.data.format

import kotlinx.serialization.Serializable
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole


@Serializable
data class FormatResolutionDetail(
    val ref: DataRef,
    val concreteFormatReference: String?,
    val displayLabel: String,
    val selection: FormatSelectionKind,
    val basis: FormatResolutionBasis,
    val reason: String,
    val warning: String? = null,
    val role: DataRole = DataRole.main,
    val resolvedEncoding: String? = null,
    val columnsLocked: Boolean = false
) {
    init {
        require(displayLabel.isNotBlank()) { "Resolved format label must not be blank" }
        require(reason.isNotBlank()) { "Format-resolution reason must not be blank" }
        require(warning == null || warning.isNotBlank()) { "Format-resolution warning must not be blank" }
        require(resolvedEncoding == null || resolvedEncoding.isNotBlank()) {
            "Resolved encoding must not be blank"
        }
    }
}
