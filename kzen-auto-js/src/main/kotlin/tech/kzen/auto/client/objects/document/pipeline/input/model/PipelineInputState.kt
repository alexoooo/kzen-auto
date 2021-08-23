package tech.kzen.auto.client.objects.document.pipeline.input.model

import tech.kzen.auto.client.objects.document.pipeline.input.browse.model.InputBrowserState
import tech.kzen.auto.client.objects.document.pipeline.input.select.model.InputSelectedState


data class PipelineInputState(
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
