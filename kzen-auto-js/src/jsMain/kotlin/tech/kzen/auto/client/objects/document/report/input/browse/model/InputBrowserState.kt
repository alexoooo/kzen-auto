package tech.kzen.auto.client.objects.document.report.input.browse.model

import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf


data class InputBrowserState(
    val browserInfoLoading: Boolean = false,
    val browserInfoError: String?  = null,
    val browserInfo: InputBrowserInfo? = null,
    val browserDirChangeRequest: DataLocation? = null,
    val browserChecked: PersistentSet<DataLocation> = persistentSetOf()
) {
    fun anyLoading(): Boolean {
        return browserInfoLoading ||
                browserDirChangeRequest != null
    }
}