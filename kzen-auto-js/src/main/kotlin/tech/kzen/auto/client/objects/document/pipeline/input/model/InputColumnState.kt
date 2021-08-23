package tech.kzen.auto.client.objects.document.pipeline.input.model


data class InputColumnState(
    val columnListingLoaded: Boolean = false,
    val columnListingLoading: Boolean = false,
    val columnListing: List<String>? = null,
    val columnListingError: String? = null,
)