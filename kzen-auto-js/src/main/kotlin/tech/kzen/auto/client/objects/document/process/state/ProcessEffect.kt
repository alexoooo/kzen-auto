package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertMapEntryInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess
import tech.kzen.lib.platform.collect.persistentListOf


object ProcessEffect {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun effect(
        state: ProcessState,
        prevState: ProcessState,
        action: ProcessAction
    ): ProcessAction? {
        return when (action) {
            InitiateProcessEffect ->
                ListInputsRequest

            ListInputsRequest ->
                loadFileListing(state)

            ListColumnsRequest ->
                loadColumnListing(state)

            is ListInputsResponse ->
                ListColumnsRequest

            is FilterAddRequest ->
                submitFilterAdd(state, action.columnName)

            is FilterRemoveRequest ->
                submitFilterRemove(state, action.columnName)

            else -> null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadFileListing(
        state: ProcessState
    ): ProcessAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            FilterConventions.actionParameter to FilterConventions.actionListFiles)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<String>

                ListInputsResult(resultValue)
            }

            is ExecutionFailure -> {
                ListInputsError(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadColumnListing(
        state: ProcessState
    ): ProcessAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            FilterConventions.actionParameter to FilterConventions.actionListColumns)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<String>

                ListColumnsResponse(resultValue)
            }

            is ExecutionFailure -> {
                ListInputsError(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun submitFilterAdd(
        state: ProcessState,
        columnName: String
    ): ProcessAction {
        val columnAttributeSegment = AttributeSegment.ofKey(columnName)

        val result = ClientContext.mirroredGraphStore.apply(
            InsertMapEntryInAttributeCommand(
                state.mainLocation,
                FilterConventions.criteriaAttributePath,
                PositionIndex(0),
                columnAttributeSegment,
                ListAttributeNotation.empty,
                true
            ))

        return when (result) {
            is MirroredGraphError ->
                FilterAddError(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FilterAddResponse
        }
    }


    private suspend fun submitFilterRemove(
        state: ProcessState,
        columnName: String
    ): ProcessAction {
        val columnAttributeSegment = AttributeSegment.ofKey(columnName)
        val columnAttributePath = FilterConventions.criteriaAttributePath.copy(
            nesting = AttributeNesting(persistentListOf(columnAttributeSegment)))

        val result = ClientContext.mirroredGraphStore.apply(
            RemoveInAttributeCommand(
                state.mainLocation,
                columnAttributePath,
                true)
        )

        return when (result) {
            is MirroredGraphError ->
                FilterRemoveError(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FilterRemoveResponse
        }
    }
}