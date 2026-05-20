package tech.kzen.auto.client.objects.document.custom.raw


data class CustomRawState(
    val editorValue: String,
    val saving: Boolean = false,
    val lastError: String? = null,
    val editorModified: Boolean = false
)
