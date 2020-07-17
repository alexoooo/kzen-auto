package tech.kzen.auto.client.objects.document.process.state


object ProcessReducer {
    fun reduce(
        state: ProcessState,
        action: ProcessAction
    ): ProcessState {
        return when (action) {
            ListInputsRequest ->
                state.copy(
                    fileListingLoading = true,
                    fileListing = null,
                    fileListingError = null)

            is ListInputsResponse ->
                state.copy(
                    fileListing = action.fileListing,
                    fileListingLoaded = true,
                    fileListingLoading = false)

            is ListInputsError ->
                state.copy(
                    fileListingError = action.message,
                    fileListingLoaded = true,
                    fileListingLoading = false)

//            else ->
//                state
        }
    }
}