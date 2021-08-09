package tech.kzen.auto.client.objects.document.pipeline.input.browse.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.toPersistentSet


class InputBrowserStore(
    private val store: PipelineStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun browserLoadInfoAsync() {
        browserBeforeLoadInfo()
        async {
            browserPerformLoadInfo()
        }
    }


    private fun browserBeforeLoadInfo() {
        store.update { state ->
            state.withInputBrowser { it.copy(
                browserInfoLoading = true,
                browserInfoError = null
            ) }
        }
    }


    private fun browserLoadInfoAborted(notationError: String) {
        store.update { state -> state
            .withNotationError(notationError)
            .withInputBrowser { it.copy(browserInfoLoading = false) }
        }
    }


    private suspend fun browserPerformLoadInfo() {
        store.updateAsync { state ->
            val result = browserInfo(state.mainLocation)

            val available = result.valueOrNull()?.files?.map { it.path }?.toSet() ?: setOf()
            val selectedAvailable = state.input.browser.browserChecked.filter { it in available }.toPersistentSet()

            state.withInputBrowser { it.copy(
                browserInfoLoading = false,
                browserInfoError = result.errorOrNull(),
                browserInfo = result.valueOrNull(),
                browserChecked = selectedAvailable
            ) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun browserDirSelectedAsync(dir: DataLocation) {
        if (store.state().inputSpec().browser.directory == dir) {
            return
        }

        store.update { state -> state
            .withNotationError(null)
            .withInputBrowser { it.copy(browserDirChangeRequest = dir) }
        }

        async {
            delay(1)
            browserBeforeLoadInfo()

            delay(10)
            val error = browserSelectDir(store.mainLocation(), dir)

            if (error != null) {
                browserLoadInfoAborted(error)
                return@async
            }

            delay(10)
            browserPerformLoadInfo()

            delay(10)
            store.update { state -> state
                .withNotationError(null)
                .withInputBrowser { it.copy(browserDirChangeRequest = null) }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun browserSelectionUpdate(nextSelected: PersistentSet<DataLocation>) {
        store.update { state -> state
            .withInputBrowser { it.copy(browserChecked = nextSelected) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun browserFilterUpdateAsync(nextFilter: String) {
        browserBeforeLoadInfo()

        async {
            delay(1)
            val updateError = browserUpdateFilter(store.mainLocation(), nextFilter)

            if (updateError != null) {
                browserLoadInfoAborted(updateError)
                return@async
            }

            delay(10)
            browserPerformLoadInfo()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun browserInfo(mainLocation: ObjectLocation): ClientResult<InputBrowserInfo> {
        val result = ClientContext.restClient.performDetached(
            mainLocation,
            PipelineConventions.actionParameter to PipelineConventions.actionBrowseFiles)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Any>

                val inputBrowserInfo = InputBrowserInfo.ofCollection(resultValue)
                ClientResult.ofSuccess(inputBrowserInfo)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    private suspend fun browserSelectDir(mainLocation: ObjectLocation, dir: DataLocation): String? {
        val command = InputSpec.browseCommand(mainLocation, dir)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    private suspend fun browserUpdateFilter(
        mainLocation: ObjectLocation,
        filter: String
    ): String? {
        val command = InputSpec.filterCommand(mainLocation, filter)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }
}