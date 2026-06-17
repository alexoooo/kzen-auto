package tech.kzen.auto.client.objects.document.common.raw


data class DocumentRawState(
    val editorValue: String,
    val saving: Boolean = false,
    val lastError: String? = null
)
