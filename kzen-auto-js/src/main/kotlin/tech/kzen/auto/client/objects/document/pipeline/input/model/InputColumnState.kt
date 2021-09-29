package tech.kzen.auto.client.objects.document.pipeline.input.model

import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo


data class InputColumnState(
    val columnListingLoaded: Boolean = false,
    val columnListingLoading: Boolean = false,

//    val columnListing: List<String>? = null,
    val columnListing: AnalysisColumnInfo? = null,

    val columnListingError: String? = null,
)