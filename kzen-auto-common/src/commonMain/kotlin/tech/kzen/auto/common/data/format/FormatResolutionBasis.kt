package tech.kzen.auto.common.data.format

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
enum class FormatResolutionBasis {
    @SerialName("override")
    Override,

    @SerialName("extension")
    Extension,

    @SerialName("content")
    Content,

    @SerialName("fallback")
    Fallback;

    val wireValue: String
        get() = when (this) {
            Override -> "override"
            Extension -> "extension"
            Content -> "content"
            Fallback -> "fallback"
        }

    companion object {
        fun ofWireValue(value: String): FormatResolutionBasis = entries.firstOrNull { it.wireValue == value }
            ?: throw IllegalArgumentException("Unknown format-resolution basis: $value")
    }
}
