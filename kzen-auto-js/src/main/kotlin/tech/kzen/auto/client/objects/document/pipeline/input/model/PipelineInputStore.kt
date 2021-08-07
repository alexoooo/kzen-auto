package tech.kzen.auto.client.objects.document.pipeline.input.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.input.browse.InputBrowserEndpoint
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.toPersistentSet


class PipelineInputStore(
    private val store: PipelineStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun initAsync() {
        if (store.state().inputSpec().selection.locations.isEmpty()) {
            browserLoadInfoAsync()
        }
        else {
            selectionLoadInfoAsync()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun browserLoadInfoAsync() {
        browserBeforeLoadInfo()
        async {
            browserPerformLoadInfo()
        }
    }


    private fun browserBeforeLoadInfo() {
        store.update { state -> state.copy(
            input = state.input.copy(
                browserInfoLoading = true,
                browserInfoError = null
            ))
        }
    }


    private fun browserLoadInfoAborted(notationError: String) {
        store.update { state -> state.copy(
            input = state.input.copy(
                browserInfoLoading = false
            ),
            notationError = notationError
        )}
    }


    private suspend fun browserPerformLoadInfo() {
        store.updateAsync { state ->
            val result = InputBrowserEndpoint.browserInfo(state.mainLocation)

            val available = result.valueOrNull()?.files?.map { it.path }?.toSet() ?: setOf()
            val selectedAvailable = state.input.browserChecked.filter { it in available }.toPersistentSet()

            state.copy(input = state.input.copy(
                browserInfoLoading = false,
                browserInfoError = result.errorOrNull(),
                browserInfo = result.valueOrNull(),
                browserChecked = selectedAvailable
            ))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun browserDirSelectedAsync(dir: DataLocation) {
        if (store.state().inputSpec().browser.directory == dir) {
            return
        }

        store.update { state -> state.copy(
            input = state.input.copy(
                browserDirChangeRequest = dir),
            notationError = null)
        }

        async {
            delay(1)
            browserBeforeLoadInfo()

            delay(10)
            val error = InputBrowserEndpoint.browserSelectDir(store.mainLocation(), dir)

            if (error != null) {
                browserLoadInfoAborted(error)
                return@async
            }

            delay(10)
            browserPerformLoadInfo()

            delay(10)
            store.update { state -> state.copy(
                input = state.input.copy(
                    browserDirChangeRequest = null),
                notationError = null
            )}
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun browserSelectionUpdate(nextSelected: PersistentSet<DataLocation>) {
        store.update { state -> state.copy(
            input = state.input.copy(
                browserChecked = nextSelected
            ))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun browserFilterUpdateAsync(nextFilter: String) {
        browserBeforeLoadInfo()

        async {
            delay(1)
            val updateError = InputBrowserEndpoint.browserUpdateFilter(store.mainLocation(), nextFilter)

            if (updateError != null) {
                browserLoadInfoAborted(updateError)
                return@async
            }

            delay(10)
            browserPerformLoadInfo()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun selectionBeforeChange() {
        store.update { state -> state.copy(
            input = state.input.copy(
                selectionChangeLoading = true),
            notationError = null)
        }
    }


    fun selectionAddAsync(dataLocations: List<DataLocation>) {
        selectionBeforeChange()

        async {
            delay(1)
            val dataLocationSpecs = InputBrowserEndpoint.selectionDefaultFormats(
                store.mainLocation(), dataLocations)

            when (dataLocationSpecs) {
                is ClientSuccess -> {
                    val error = InputBrowserEndpoint.selectionAddFiles(
                        store.mainLocation(), dataLocationSpecs.value)

                    store.update { state -> state.copy(
                        input = state.input.copy(
                            selectionChangeLoading = false,
                            selectionDefaultFormatsError = null),
                        notationError = error
                    )}
                }

                is ClientError ->
                    store.update { state -> state.copy(
                        input = state.input.copy(
                            selectionChangeLoading = false,
                            selectionDefaultFormatsError = dataLocationSpecs.message
                        ))
                    }
            }
        }
    }


    fun selectionRemoveAsync(dataLocations: List<DataLocation>) {
        val dataLocationsSet = dataLocations.toSet()

        val inputSelectionSpec = store.state().inputSpec().selection
        val removedSpecs = inputSelectionSpec.locations.filter { it.location in dataLocationsSet }

        if (removedSpecs.isEmpty()) {
            return
        }

        selectionBeforeChange()

        async {
            delay(1)
            val error = InputBrowserEndpoint.selectionRemoveFiles(store.mainLocation(), removedSpecs)

            delay(10)
            store.update { state -> state.copy(
                input = state.input.copy(
                    selectionChangeLoading = false),
                notationError = error)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun selectionLoadInfoAsync() {
        selectionBeforeLoadInfo()
        async {
            selectionPerformLoadInfo()
        }
    }


    private fun selectionBeforeLoadInfo() {
        store.update { state -> state.copy(
            input = state.input.copy(
                selectionInfoLoading = true,
                selectionInfoError = null
            ))
        }
    }


    private suspend fun selectionPerformLoadInfo() {
        store.updateAsync { state ->
            val result = InputBrowserEndpoint.selectionInfo(state.mainLocation)

            state.copy(input = state.input.copy(
                selectionInfoLoading = false,
                selectionInfoError = result.errorOrNull(),
                selectionInfo = result.valueOrNull()
            ))
        }
    }
}