package tech.kzen.auto.client.objects.document.report.input.model

import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo


data class InputColumnState(
    val columnListingLoaded: Boolean = false,
    val columnListingLoading: Boolean = false,
    val columnListingError: String? = null,
    val analysisColumnInfo: AnalysisColumnInfo? = null
)