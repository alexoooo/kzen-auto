package tech.kzen.auto.client.objects.document.pipeline.input.select.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
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
    suspend fun init() {
        selectionBeforeLoadInfo()

        delay(10)
        selectionPerformLoadInfo()
    }


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


    //-----------------------------------------------------------------------------------------------------------------
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


    private suspend fun selectionAddFiles(
        paths: List<InputDataSpec>
    ): String? {
        val command = InputSpec.addSelectedCommand(store.mainLocation(), paths)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    //-----------------------------------------------------------------------------------------------------------------
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


    private suspend fun selectionRemoveFiles(
        paths: List<InputDataSpec>
    ): String? {
        val command = InputSpec.removeSelectedCommand(store.mainLocation(), paths)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
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


    private suspend fun setGroupBy(
        groupBy: String
    ): String? {
        val command = InputSpec.setGroupByCommand(
            store.mainLocation(), groupBy)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
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


    private suspend fun selectDataType(
        dataType: ClassName
    ): String? {
        val command = InputSpec.selectDataTypeCommand(store.mainLocation(), dataType)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
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


    //-----------------------------------------------------------------------------------------------------------------
    fun setFormatAsync(
        format: CommonPluginCoordinate,
        dataSpecs: List<InputDataSpec>
    ) {
        if (format.isDefault()) {
            val selectedLocations = dataSpecs.map { it.location }

            async {
                val result = defaultFormats(selectedLocations)
                    ?: return@async

                val defaultCoordinateSet = result.map { it.processorDefinitionCoordinate }.toSet()

                if (defaultCoordinateSet.size == 1) {
                    val defaultCoordinate = defaultCoordinateSet.single()

                    val changedLocations = dataSpecs
                        .filter { it.processorDefinitionCoordinate != defaultCoordinate }
                        .map { it.location }

                    if (changedLocations.isEmpty()) {
                        return@async
                    }

                    delay(10)
                    beforeNotationChange()

                    delay(10)
                    val error = selectSingleFormat(defaultCoordinate, changedLocations)

                    afterNotationChange(error)

                    if (error != null) {
                        return@async
                    }

                    delay(10)
                    selectionBeforeLoadInfo()

                    delay(10)
                    selectionPerformLoadInfo()
                }
                else {
                    val locationFormats = result.associate {
                        it.location to it.processorDefinitionCoordinate
                    }

                    delay(10)
                    beforeNotationChange()

                    delay(10)
                    val error = selectMultiFormat(locationFormats)

                    afterNotationChange(error)

                    if (error != null) {
                        return@async
                    }

                    delay(10)
                    selectionBeforeLoadInfo()

                    delay(10)
                    selectionPerformLoadInfo()
                }
            }
        }
        else {
            val changedLocations = dataSpecs
                .filter { it.processorDefinitionCoordinate != format }
                .map { it.location }

            if (changedLocations.isEmpty()) {
                return
            }

            async {
                delay(1)
                beforeNotationChange()

                delay(10)
                val error = selectSingleFormat(format, changedLocations)

                afterNotationChange(error)

                if (error != null) {
                    return@async
                }

                delay(10)
                selectionBeforeLoadInfo()

                delay(10)
                selectionPerformLoadInfo()
            }
        }
    }


    private suspend fun selectSingleFormat(
        format: CommonPluginCoordinate,
        dataLocations: List<DataLocation>
    ): String? {
        val command = InputSpec.selectFormatCommand(
            store.mainLocation(),
            store.state().inputSpec().selection,
            dataLocations,
            format)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    private suspend fun selectMultiFormat(
        locationFormats: Map<DataLocation, CommonPluginCoordinate>
    ): String? {
        val command = InputSpec.selectMultiFormatCommand(
            store.mainLocation(),
            store.state().inputSpec().selection,
            locationFormats)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }
}