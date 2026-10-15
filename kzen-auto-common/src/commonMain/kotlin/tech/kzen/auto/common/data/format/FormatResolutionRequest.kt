package tech.kzen.auto.common.data.format

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataRef


data class FormatResolutionRequest(
    val context: DataContext,
    val ref: DataRef,
    val explicitEncoding: String?
) {
    init {
        require(explicitEncoding == null || explicitEncoding.isNotBlank()) {
            "Explicit encoding must not be blank"
        }
    }
}
