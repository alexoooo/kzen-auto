package tech.kzen.auto.client.objects.document.report.preview.model

import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.common.paradigm.logic.run.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess


class ReportPreviewStore(
    private val store: ReportStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun init() {
        lookupSummaryWithFallback()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun lookupSummaryWithFallbackAsync() {
        async {
            lookupSummaryWithFallback()
        }
    }


    suspend fun lookupSummaryWithFallback() {
        if (! store.state().previewSpec(true).enabled ||
                store.state().output.outputInfo?.status == OutputStatus.Missing
        ) {
            store.update { state -> state.withPreviewFiltered {
                it.copy(
                    tableSummary = null,
                    previewError = null
                )
            } }
            return
        }

        val logicRunInfo = store.state().clientLogicState.logicStatus?.active

        if (logicRunInfo != null) {
            val runId = logicRunInfo.id
            val executionId = logicRunInfo.frame.executionId
            val onlineResult = summaryOnline(runId, executionId)
            val onlineError = onlineResult.errorOrNull()
            if (onlineError == null) {
                store.update { state -> state.withPreviewFiltered {
                    it.copy(
                        tableSummary = onlineResult.valueOrNull(),
                        previewError = null
                    )
                } }
                return
            }
            else if (! LogicConventions.isMissingError(onlineError, runId, executionId)) {
                store.update { state -> state.withPreviewFiltered {
                    it.copy(
                        tableSummary = null,
                        previewError = onlineError
                    )
                } }
                return
            }
        }

        val offlineResult = summaryOffline()

        store.update { state -> state.withPreviewFiltered {
            it.copy(
                tableSummary = offlineResult.valueOrNull(),
                previewError = offlineResult.errorOrNull()
            )
        } }
    }


    private suspend fun summaryOnline(
        logicRunId: LogicRunId,
        logicExecutionId: LogicExecutionId
    ): ClientResult<TableSummary> {
        val result = ClientContext.restClient.logicRequest(
            logicRunId,
            logicExecutionId,
            ReportConventions.paramAction to ReportConventions.actionSummaryOnline)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Map<String, Any>>
                val value = TableSummary.fromCollection(resultValue)
                ClientResult.ofSuccess(value)
            }

            is ExecutionFailure -> {
                ClientResult.ofError(result.errorMessage)
            }
        }
    }


    private suspend fun summaryOffline(): ClientResult<TableSummary> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            ReportConventions.paramAction to ReportConventions.actionSummaryOffline)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Map<String, Any>>
                val value = TableSummary.fromCollection(resultValue)
                ClientResult.ofSuccess(value)
            }

            is ExecutionFailure -> {
                ClientResult.ofError(result.errorMessage)
            }
        }
    }
}