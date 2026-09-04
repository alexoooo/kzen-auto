package tech.kzen.auto.common.data.format.detection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
enum class FormatHintClass {
    @SerialName("structured-family")
    StructuredFamily,

    @SerialName("generic-text")
    GenericText,

    @SerialName("semantic-text")
    SemanticText
}
