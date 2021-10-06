package tech.kzen.auto.client.objects.document.report.input.select.model

import tech.kzen.auto.common.objects.document.plugin.model.ProcessorDefinerDetail
import tech.kzen.auto.common.objects.document.report.listing.InputSelectedInfo
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf


data class InputSelectedState(
    val selectedInfoLoading: Boolean = false,
    val selectedInfoError: String?  = null,
    val selectedInfo: InputSelectedInfo? = null,

    val selectedRequestLoading: Boolean = false,

    val selectedDefaultFormatsError: String? = null,

    val dataTypes: List<ClassName>? = null,
    val selectedDataTypesError: String? = null,

    val typeFormats: List<ProcessorDefinerDetail>? = null,
    val selectedTypeFormatsError: String? = null,

    val selectedChecked: PersistentSet<DataLocation> = persistentSetOf()
) {
    fun anyLoading(): Boolean {
        return selectedInfoLoading ||
                selectedRequestLoading
    }

    fun selectionError(): String? {
        return selectedInfoError
            ?: selectedDefaultFormatsError
            ?: selectedDataTypesError
    }
}