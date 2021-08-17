package tech.kzen.auto.client.objects.document.pipeline.input.model

import tech.kzen.auto.client.objects.document.pipeline.input.browse.model.InputBrowserStore
import tech.kzen.auto.client.objects.document.pipeline.input.select.model.InputSelectedStore
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore


class PipelineInputStore(
    private val store: PipelineStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    val browser = InputBrowserStore(store)
    val selected = InputSelectedStore(store)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun init() {
        if (store.state().inputSpec().selection.locations.isEmpty()) {
            browser.init()
        }
        else {
            selected.init()
        }
    }
}