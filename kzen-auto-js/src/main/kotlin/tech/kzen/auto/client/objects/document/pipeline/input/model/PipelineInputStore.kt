package tech.kzen.auto.client.objects.document.pipeline.input.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.input.browse.InputBrowserEndpoint
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.util.data.DataLocation


class PipelineInputStore(
    private val store: PipelineStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun initAsync() {
        beforeLoadInfo()

        async {
            performLoadInfo()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun beforeLoadInfo() {
        store.update { state -> state.copy(
            input = state.input.copy(
                infoLoading = true,
                infoError = null
            ))
        }
    }


    private suspend fun performLoadInfo() {
        store.updateAsync { state ->
            val result = InputBrowserEndpoint.browse(state.mainLocation)
            state.copy(input = state.input.copy(
                infoLoading = false,
                infoError = result.errorOrNull(),
                inputBrowserInfo = result.valueOrNull()
            ))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun browseDirSelectedAsync(dir: DataLocation) {
        if (store.state().inputSpec().browser.directory == dir) {
            return
        }

        store.update { state -> state.copy(
            input = state.input.copy(
                browserDirChangeRequest = dir,
                browserDirChangeError = null
            ))
        }

        async {
            delay(1)
            beforeLoadInfo()

            delay(10)
            val error = InputBrowserEndpoint.selectDir(store.mainLocation(), dir)

            delay(10)
            performLoadInfo()

            delay(10)
            store.update { state ->
                state.copy(input = state.input.copy(
                    browserDirChangeRequest = null,
                    browserDirChangeError = error
                ))
            }
        }
    }
}