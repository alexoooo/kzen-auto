package tech.kzen.auto.client.objects.document.pipeline.input.select.model

import tech.kzen.auto.common.objects.document.report.listing.InputSelectedInfo
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf


data class InputSelectedState(
    val selectedInfoLoading: Boolean = false,
    val selectedInfoError: String?  = null,
    val selectedInfo: InputSelectedInfo? = null,
    val selectedChangeLoading: Boolean = false,
    val selectedDefaultFormatsError: String? = null,
    val selectedChecked: PersistentSet<DataLocation> = persistentSetOf()
) {
    fun anyLoading(): Boolean {
        return selectedInfoLoading ||
                selectedChangeLoading
    }

    fun selectionError(): String? {
        return selectedInfoError ?: selectedDefaultFormatsError
    }
}