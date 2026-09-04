package tech.kzen.auto.common.data.format

import kotlinx.serialization.Serializable


@Serializable
data class FormatOverrideEditorMetadata(
    val editorReference: String,
    val label: String
) {
    init {
        require(editorReference.isNotBlank()) { "Format override editor reference must not be blank" }
        require(label.isNotBlank()) { "Format override editor label must not be blank" }
    }
}
