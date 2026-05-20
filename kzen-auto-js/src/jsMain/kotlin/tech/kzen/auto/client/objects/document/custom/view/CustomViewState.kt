package tech.kzen.auto.client.objects.document.custom.view


data class CustomViewState(
    val dragSourceIndex: Int? = null,
    val dragOverIndex: Int? = null,
    val dropAfter: Boolean = false
)
