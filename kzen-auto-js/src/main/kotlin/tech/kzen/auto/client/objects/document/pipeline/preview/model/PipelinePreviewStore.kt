package tech.kzen.auto.client.objects.document.pipeline.preview.model

import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.v1.model.LogicConventions
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId


class PipelinePreviewStore(
    private val store: PipelineStore
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

        val logicRunInfo = store.state().run.logicStatus?.active

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

        if (offlineResult.errorOrNull() == null) {
            store.run.lookupProgressOffline()
        }
    }


    private suspend fun summaryOnline(
        logicRunId: LogicRunId,
        logicExecutionId: LogicExecutionId
    ): ClientResult<TableSummary> {
        val result = ClientContext.restClient.logicRequest(
            logicRunId,
            logicExecutionId,
            PipelineConventions.actionParameter to PipelineConventions.actionSummaryOnline
        )

//        val result = ClientContext.restClient.performDetached(
//            store.mainLocation(),
//            PipelineConventions.actionParameter to PipelineConventions.actionSummaryOnline,
//            LogicConventions.runIdKey to logicRunId.value,
//            LogicConventions.executionIdKey to logicExecutionId.value,
//        )

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
            PipelineConventions.actionParameter to PipelineConventions.actionSummaryOffline)

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