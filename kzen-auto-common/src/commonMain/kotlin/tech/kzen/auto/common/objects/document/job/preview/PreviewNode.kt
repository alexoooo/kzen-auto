package tech.kzen.auto.common.objects.document.job.preview

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Detached display content. Text includes scalar formatting; children preserve field order and identity. */
@Serializable
data class PreviewNode(
    val kind: String,
    val text: String,
    val children: List<PreviewNode> = emptyList(),
    val name: String = "",
    val occurrence: Int = 0,
    val partial: Boolean = false
) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    companion object {
        const val progressKey = "previewItems"
        const val limitedKey = "previewWindowLimited"

        fun decode(text: String): PreviewNode = Json.decodeFromString(serializer(), text)
    }
}
