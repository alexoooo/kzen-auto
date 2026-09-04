package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind


class ConfiguredRecordFormatPreflight internal constructor(
    val reference: String,
    val format: ConfiguredRecordFormat
) {
    val selectionKind: FormatSelectionKind
        get() = format.selectionKind


    suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult {
        val result = format.resolve(request)
        if (selectionKind == FormatSelectionKind.Automatic) {
            return result
        }
        return result.copy(detail = result.detail.copy(concreteFormatReference = reference))
    }
}
