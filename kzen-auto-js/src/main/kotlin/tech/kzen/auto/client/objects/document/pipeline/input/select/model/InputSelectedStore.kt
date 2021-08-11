package tech.kzen.auto.client.objects.document.pipeline.input.select.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.input.browse.InputBrowserEndpoint
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.collect.PersistentSet


class InputSelectedStore(
    private val store: PipelineStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    private fun selectionBeforeChange() {
        store.update { state -> state
            .withInputSelected { it.copy(selectedChangeLoading = true) }
            .withNotationError(null)
        }
    }


    fun selectionAddAsync(dataLocations: List<DataLocation>) {
        selectionBeforeChange()

        async {
            delay(1)
            val dataLocationSpecs = InputBrowserEndpoint.selectionDefaultFormats(
                store.mainLocation(), dataLocations)

            if (dataLocationSpecs is ClientError) {
                store.update { state -> state
                    .withInputSelected { it.copy(
                        selectedChangeLoading = false,
                        selectedDefaultFormatsError = dataLocationSpecs.message)
                    }
                }
                return@async
            }

            val error = InputBrowserEndpoint.selectionAddFiles(
                store.mainLocation(), (dataLocationSpecs as ClientSuccess).value)

            store.update { state -> state
                .withInputSelected { it.copy(
                    selectedChangeLoading = false,
                    selectedDefaultFormatsError = null)
                }
                .withNotationError(error)
            }

            if (error != null) {
                return@async
            }

            delay(10)
            selectionBeforeLoadInfo()

            delay(10)
            selectionPerformLoadInfo()
        }
    }


    fun selectionRemoveAsync(dataLocations: Collection<DataLocation>) {
        val dataLocationsSet = (dataLocations as? Set) ?: dataLocations.toSet()

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
            store.update { state -> state
                .withInputSelected { it.copy(
                    selectedChangeLoading = false,
                    selectedChecked = it.selectedChecked.removeAll(dataLocations)
                ) }
                .withNotationError(error)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun checkedUpdate(nextChecked: PersistentSet<DataLocation>) {
        store.update { state -> state
            .withInputSelected { it.copy(selectedChecked = nextChecked) }
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
        store.update { state -> state
            .withInputSelected { it.copy(
                selectedInfoLoading = true,
                selectedInfoError = null)
            }
        }
    }


    private suspend fun selectionPerformLoadInfo() {
        store.updateAsync { state ->
            val result = InputBrowserEndpoint.selectionInfo(state.mainLocation)

            state.withInputSelected { it.copy(
                selectedInfoLoading = false,
                selectedInfoError = result.errorOrNull(),
                selectedInfo = result.valueOrNull())
            }
        }
    }
}