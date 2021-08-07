package tech.kzen.auto.client.objects.document.pipeline.input.model

import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.listing.InputSelectionInfo
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf


data class PipelineInputState(
    val browserInfoLoading: Boolean = false,
    val browserInfoError: String?  = null,
    val browserInfo: InputBrowserInfo? = null,
    val browserDirChangeRequest: DataLocation? = null,
//    val browserDirChangeError: String? = null,
    val browserChecked: PersistentSet<DataLocation> = persistentSetOf(),
//    val browserFilterChangeError: String? = null,

    val selectionInfoLoading: Boolean = false,
    val selectionInfoError: String?  = null,
    val selectionInfo: InputSelectionInfo? = null,
    val selectionChangeLoading: Boolean = false,
    val selectionDefaultFormatsError: String? = null,
//    val selectionChangeError: String? = null,

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
        return browserInfoLoading ||
                browserDirChangeRequest != null ||
                selectionInfoLoading ||
                selectionChangeLoading
    }

//    fun browserChangeError(): String? {
//        return browserDirChangeError //?: browserFilterChangeError
//    }

    fun selectionError(): String? {
        return selectionInfoError ?: selectionDefaultFormatsError
    }
}
