package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess


object ProcessEffect {
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

            else -> null
        }
    }


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
}