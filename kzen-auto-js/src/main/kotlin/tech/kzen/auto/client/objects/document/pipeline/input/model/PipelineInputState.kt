package tech.kzen.auto.client.objects.document.pipeline.input.model

import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.util.data.DataLocation


data class PipelineInputState(
    val infoLoading: Boolean = false,
    val infoError: String?  = null,
    var inputBrowserInfo: InputBrowserInfo? = null,

    val browserDirChangeRequest: DataLocation? = null,
    val browserDirChangeError: String? = null,

//    val inputSelectionLoaded: Boolean = false,
//    val inputSelectionLoading: Boolean = false,
//    val inputSelectionError: String? = null,
//    val inputSelection: InputSelectionInfo? = null,
//
//    val inputBrowserLoaded: Boolean = false,
//    val inputBrowserLoading: Boolean = false,
//    val inputBrowser: List<DataLocationInfo>? = null,
//    val inputBrowseDir: DataLocation? = null,
//    val inputBrowseError: String? = null,
//
//    val columnListingLoaded: Boolean = false,
//    val columnListingLoading: Boolean = false,
//    val columnListing: List<String>? = null,
//    val columnListingError: String? = null,
) {
    fun anyLoading(): Boolean {
        return infoLoading ||
                browserDirChangeRequest != null
    }
}
