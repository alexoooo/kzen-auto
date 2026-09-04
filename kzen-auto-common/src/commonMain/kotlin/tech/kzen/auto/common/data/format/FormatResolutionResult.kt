package tech.kzen.auto.common.data.format

import kotlinx.serialization.Serializable
import tech.kzen.auto.common.data.read.ResolvedReadSpec


@Serializable
data class FormatResolutionResult(
    val resolvedRead: ResolvedReadSpec,
    val detail: FormatResolutionDetail
)
