package tech.kzen.auto.client.objects.document.report.input.model

import tech.kzen.auto.client.objects.document.report.input.browse.model.InputBrowserState
import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedState


data class ReportInputState(
    val browser: InputBrowserState = InputBrowserState(),
    val selected: InputSelectedState = InputSelectedState(),
    val column: InputColumnState = InputColumnState()
) {
    fun anyLoading(): Boolean {
        return browser.anyLoading() ||
                selected.anyLoading() ||
                column.columnListingLoading
    }
}
