package tech.kzen.auto.plugin.api.data

import tech.kzen.auto.common.data.format.FormatMaterializationRequest
import tech.kzen.auto.common.data.format.FormatMaterializationResult


interface FormatAuthoringCapability {
    val authoringIdentity: String

    val supportsColumnLocking: Boolean
        get() = false

    fun materialize(request: FormatMaterializationRequest): FormatMaterializationResult
}
