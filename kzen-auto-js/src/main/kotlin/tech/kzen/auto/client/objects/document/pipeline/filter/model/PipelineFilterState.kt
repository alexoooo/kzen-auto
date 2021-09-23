package tech.kzen.auto.client.objects.document.pipeline.filter.model


data class PipelineFilterState(
    val filterLoading: Boolean = false,
    val filterError: String? = null
)