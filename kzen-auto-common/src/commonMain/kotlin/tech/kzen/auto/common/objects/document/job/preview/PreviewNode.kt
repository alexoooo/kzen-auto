package tech.kzen.auto.common.objects.document.job.preview

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Detached display content. Text includes scalar formatting; children preserve field order and identity. An item's
 * [metadata] is the value's metadata captured the same way (absent when the value has none).
 */
@Serializable
data class PreviewNode(
    val kind: String,
    val text: String,
    val children: List<PreviewNode> = emptyList(),
    val name: String = "",
    val occurrence: Int = 0,
    val partial: Boolean = false,
    val metadata: PreviewNode? = null
) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    companion object {
        const val progressKey = "previewItems"
        const val limitedKey = "previewWindowLimited"

        fun decode(text: String): PreviewNode = Json.decodeFromString(serializer(), text)
    }
}
