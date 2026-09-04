package tech.kzen.auto.common.data.format

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
enum class FormatSelectionKind {
    @SerialName("explicit")
    Explicit,

    @SerialName("automatic")
    Automatic;

    val wireValue: String
        get() = when (this) {
            Explicit -> "explicit"
            Automatic -> "automatic"
        }

    companion object {
        fun ofWireValue(value: String): FormatSelectionKind = entries.firstOrNull { it.wireValue == value }
            ?: throw IllegalArgumentException("Unknown format selection: $value")
    }
}
