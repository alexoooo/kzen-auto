package tech.kzen.auto.client.objects.document.pipeline.input.select.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.plugin.model.ProcessorDefinerDetail
import tech.kzen.auto.common.objects.document.report.listing.InputSelectedInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.PersistentSet


class InputSelectedStore(
    private val store: PipelineStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    private fun beforeNotationChange() {
        store.update { state -> state
            .withInputSelected { it.copy(selectedRequestLoading = true) }
            .withNotationError(null)
        }
    }


    private fun afterNotationChange(error: String?) {
        store.update { state -> state
            .withInputSelected { it.copy(selectedRequestLoading = false) }
            .withNotationError(error)
        }
    }


    fun selectionAddAsync(dataLocations: List<DataLocation>) {
        beforeNotationChange()

        async {
            delay(1)
            val dataLocationSpecs = selectionDefaultFormats(dataLocations)

            if (dataLocationSpecs is ClientError) {
                store.update { state -> state
                    .withInputSelected { it.copy(
                        selectedRequestLoading = false,
                        selectedDefaultFormatsError = dataLocationSpecs.message)
                    }
                }
                return@async
            }

            val error = selectionAddFiles(
                (dataLocationSpecs as ClientSuccess).value)

            store.update { state -> state
                .withInputSelected { it.copy(
                    selectedRequestLoading = false,
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

        beforeNotationChange()

        async {
            delay(1)
            val error = selectionRemoveFiles(removedSpecs)

            delay(10)
            store.update { state -> state
                .withInputSelected { it.copy(
                    selectedRequestLoading = false,
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun selectionBeforeLoadInfo() {
        store.update { state -> state
            .withInputSelected { it.copy(
                selectedInfoLoading = true,
                selectedInfoError = null
            ) }
        }
    }


    private suspend fun selectionPerformLoadInfo() {
        val result = selectionInfo()

        store.update { state -> state
            .withInputSelected { it.copy(
                selectedInfoLoading = false,
                selectedInfoError = result.errorOrNull(),
                selectedInfo = result.valueOrNull()
            ) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun groupByAsync(groupBy: String) {
        beforeNotationChange()

        async {
            delay(1)
            val notationError = setGroupBy(groupBy)

            delay(10)
            afterNotationChange(notationError)

            if (notationError != null) {
                return@async
            }

            delay(10)
            selectionBeforeLoadInfo()

            delay(10)
            selectionPerformLoadInfo()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun listDataTypesAsync() {
        store.update { state -> state
            .withInputSelected { it.copy(selectedRequestLoading = true) }
        }

        async {
            delay(1)
            val response = listDataTypes()

            store.update { state -> state
                .withInputSelected { it.copy(
                    selectedRequestLoading = false,
                    dataTypes = response.valueOrNull(),
                    selectedDataTypesError = response.errorOrNull()
                ) }
            }
        }
    }


    fun selectDataTypeAsync(dataType: ClassName) {
        beforeNotationChange()

        async {
            delay(1)
            val notationError = selectDataType(dataType)

            delay(10)
            store.update { state -> state
                .withInputSelected { it.copy(
                    selectedRequestLoading = false,
                    typeFormats = if (notationError == null) { null } else { it.typeFormats }
                ) }
                .withNotationError(notationError)
            }

            if (notationError != null) {
                return@async
            }

            delay(10)
            selectionBeforeLoadInfo()

            delay(10)
            selectionPerformLoadInfo()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun listTypeFormatsAsync() {
        store.update { state -> state
            .withInputSelected { it.copy(selectedRequestLoading = true) }
        }

        async {
            delay(1)
            val response = listFormats()

            store.update { state -> state
                .withInputSelected { it.copy(
                    selectedRequestLoading = false,
                    typeFormats = response.valueOrNull(),
                    selectedDataTypesError = response.errorOrNull()
                ) }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun defaultFormats(
        paths: List<DataLocation>
    ): List<InputDataSpec>? {
        store.update { state -> state
            .withInputSelected { it.copy(
                selectedRequestLoading = true,
                selectedDefaultFormatsError = null
            ) }
        }

        delay(10)
        val result = selectionDefaultFormats(paths)

        delay(10)
        store.update { state -> state
            .withInputSelected { it.copy(
                selectedRequestLoading = false,
                selectedDefaultFormatsError = result.errorOrNull()
            ) }
        }

        return result.valueOrNull()
    }


    private suspend fun selectionDefaultFormats(
        paths: List<DataLocation>
    ): ClientResult<List<InputDataSpec>> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            PipelineConventions.actionParameter to PipelineConventions.actionDefaultFormat,
            *paths.map { PipelineConventions.filesParameter to it.asString() }.toTypedArray())

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<Map<String, String>>

                val inputDataSpecs = resultValue.map { InputDataSpec.ofCollection(it) }

                ClientResult.ofSuccess(inputDataSpecs)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    suspend fun selectionAddFiles(
        paths: List<InputDataSpec>
    ): String? {
        val command = InputSpec.addSelectedCommand(store.mainLocation(), paths)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    suspend fun selectionRemoveFiles(
        paths: List<InputDataSpec>
    ): String? {
        val command = InputSpec.removeSelectedCommand(store.mainLocation(), paths)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    private suspend fun selectionInfo(): ClientResult<InputSelectedInfo> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            PipelineConventions.actionParameter to PipelineConventions.actionInputInfo)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<Map<String, Any>>

                ClientResult.ofSuccess(
                    InputSelectedInfo.ofCollection(resultValue))
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    private suspend fun setGroupBy(
        groupBy: String
    ): String? {
        val command = InputSpec.setGroupByCommand(
            store.mainLocation(), groupBy)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    private suspend fun listDataTypes(): ClientResult<List<ClassName>> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            PipelineConventions.actionParameter to PipelineConventions.actionDataTypes)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<String>

                val dataTypes = resultValue.map { ClassName(it) }

                ClientResult.ofSuccess(dataTypes)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    private suspend fun selectDataType(
        dataType: ClassName
    ): String? {
        val command = InputSpec.selectDataTypeCommand(store.mainLocation(), dataType)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    private suspend fun listFormats(): ClientResult<List<ProcessorDefinerDetail>> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            PipelineConventions.actionParameter to PipelineConventions.actionTypeFormats)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<Map<String, Any?>>

                val formats = resultValue.map { ProcessorDefinerDetail.ofCollection(it) }

                ClientResult.ofSuccess(formats)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }
}