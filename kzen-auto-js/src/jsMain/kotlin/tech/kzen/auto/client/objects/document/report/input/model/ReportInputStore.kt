package tech.kzen.auto.client.objects.document.report.input.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.report.input.browse.model.InputBrowserStore
import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedStore
import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess


class ReportInputStore(
    private val store: ReportStore
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


    fun listColumnsAsync() {
        beforeColumnListing()

        async {
            delay(1)
            val response = listColumnsRequest()

            delay(10)
            afterColumnListing(response)
        }
    }



    suspend fun listColumnsIfFlat() {
        if (store.state().analysisSpec().type != AnalysisType.FlatData) {
            return
        }

        listColumns()
    }


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
                    analysisColumnInfo = result.valueOrNull(),
                    columnListingError = result.errorOrNull()
                )
            )
        ) }
    }


    private suspend fun listColumnsRequest(): ClientResult<AnalysisColumnInfo> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            ReportConventions.paramAction to ReportConventions.actionListColumns)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultCollection = result.value.get() as Map<String, Any>

//                println("listColumnsRequest: $resultCollection")
                val resultValue = AnalysisColumnInfo.ofCollection(resultCollection)
//                println("got: $resultValue")

                ClientResult.ofSuccess(resultValue)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }
}