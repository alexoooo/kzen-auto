package tech.kzen.auto.client.objects.document.pipeline.input.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.input.browse.model.InputBrowserStore
import tech.kzen.auto.client.objects.document.pipeline.input.select.model.InputSelectedStore
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess


class PipelineInputStore(
    private val store: PipelineStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    val browser = InputBrowserStore(store)
    val selected = InputSelectedStore(store)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun init() {
        if (store.state().inputSpec().selection.locations.isEmpty()) {
            browser.init()
        }
        else {
            selected.init()

//            beforeColumnListing()
//            val response = listColumnsRequest()
//            afterColumnListing(response)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun listColumnsAsync() {
//        beforeColumnListing()
//
//        async {
//            delay(1)
//            val response = listColumnsRequest()
//
//            afterColumnListing(response)
//        }
//    }


    suspend fun listColumns() {
        delay(1)
        beforeColumnListing()

        val response = listColumnsRequest()

        delay(10)
        afterColumnListing(response)
    }


    private fun beforeColumnListing() {
        store.update { state -> state.copy(
            input = state.input.copy(
                column = state.input.column.copy(
                    columnListingLoading = true,
                    columnListingError = null
                )
            )
        ) }
    }


    private fun afterColumnListing(result: ClientResult<AnalysisColumnInfo>) {
        store.update { state -> state.copy(
            input = state.input.copy(
                column = state.input.column.copy(
                    columnListingLoading = false,
                    columnListingLoaded = true,
                    columnListing = result.valueOrNull(),
                    columnListingError = result.errorOrNull()
                )
            )
        ) }
    }


    private suspend fun listColumnsRequest(): ClientResult<AnalysisColumnInfo> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            PipelineConventions.actionParameter to PipelineConventions.actionListColumns)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultCollection = result.value.get() as Map<String, Any>
                val resultValue = AnalysisColumnInfo.ofCollection(resultCollection)
                ClientResult.ofSuccess(resultValue)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }
}