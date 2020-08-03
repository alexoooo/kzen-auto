package tech.kzen.auto.client.objects.document.process.state


object ProcessReducer {
    //-----------------------------------------------------------------------------------------------------------------
    fun reduce(
        state: ProcessState,
        action: ProcessAction
    ): ProcessState {
        return when (action) {
            //--------------------------------------------------
            InitiateProcessEffect -> state

            //--------------------------------------------------
            is ListInputsAction ->
                reduceListInputs(state, action)

            is ListColumnsAction ->
                reduceListColumns(state, action)

            is FilterAction ->
                reduceFilter(state, action)

            //--------------------------------------------------
//            else ->
//                state
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceListInputs(
        state: ProcessState,
        action: ListInputsAction
    ): ProcessState {
        return when (action) {
            ListInputsRequest -> state.copy(
                fileListingLoading = true,
                fileListing = null,
                fileListingError = null)

            is ListInputsResult -> state.copy(
                fileListing = action.fileListing,
                fileListingLoaded = true,
                fileListingLoading = false,
                columnListing = null,
                columnListingError = null)

            is ListInputsError -> state.copy(
                fileListingError = action.message,
                fileListingLoaded = true,
                fileListingLoading = false)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceListColumns(
        state: ProcessState,
        action: ListColumnsAction
    ): ProcessState {
        return when (action) {
            ListColumnsRequest -> state.copy(
                columnListingLoading = true,
                columnListing = null,
                columnListingError = null)

            is ListColumnsResponse -> state.copy(
                columnListing = action.columnListing,
                columnListingLoaded = true,
                columnListingLoading = false)

            is ListColumnsError -> state.copy(
                columnListingError = action.message,
                columnListingLoaded = true,
                columnListingLoading = false)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceFilter(
        state: ProcessState,
        action: FilterAction
    ): ProcessState {
        return when (action) {
            is FilterAddRequest -> state.copy(
                filterAddingLoading = true,
                filterAddingError = null)

            FilterAddResponse -> state.copy(
                filterAddingLoading = false)

            is FilterAddError -> state.copy(
                filterAddingLoading = false,
                filterAddingError = action.message)


            is FilterRemoveRequest -> state
            FilterRemoveResponse -> state
            is FilterRemoveError -> state
        }
    }
}