package tech.kzen.auto.client.objects.document.process.state


object ProcessReducer {
    fun reduce(
        state: ProcessState,
        action: ProcessAction
    ): ProcessState {
        return when (action) {
            InitiateProcessEffect -> state


            ListInputsRequest -> state.copy(
                fileListingLoading = true,
                fileListing = null,
                fileListingError = null)

            is ListInputsResult -> state.copy(
                fileListing = action.fileListing,
                fileListingLoaded = true,
                fileListingLoading = false)

            is ListInputsError -> state.copy(
                fileListingError = action.message,
                fileListingLoaded = true,
                fileListingLoading = false)


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

//            else ->
//                state
        }
    }
}