package tech.kzen.auto.common.data.format

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.DataContentFingerprint


data class FormatResolutionRequest(
    val context: DataContext,
    val ref: DataRef,
    val expectedFingerprint: DataContentFingerprint?,
    val hints: NormalizedFormatHints,
    val explicitEncoding: String?,
    val budget: FormatResolutionBudget = UnlimitedFormatResolutionBudget
) {
    init {
        require(explicitEncoding == null || explicitEncoding.isNotBlank()) {
            "Explicit encoding must not be blank"
        }
    }
}
