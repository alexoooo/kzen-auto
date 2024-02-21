package tech.kzen.auto.client.objects.document.report.run.model

import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.common.paradigm.logic.run.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunId
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTracePath
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTraceQuery
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess


class ReportRunStore(
    private val store: ReportStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun init() {
//        lookupStatus()

        val activeInfo = store.state().clientLogicState.logicStatus?.active
        if (activeInfo != null) {
            lookupProgress(
                LogicRunExecutionId(
                activeInfo.id, activeInfo.frame.executionId)
            )
        }
        else {
            lookupProgressOffline()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        lookupProgressActive()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun lookupProgressOfflineAsync() {
//        println("lookupProgressOfflineAsync - ${store.state().output.outputInfo}")
        val runExecutionId = store.state().output.outputInfo?.runExecutionId
            ?: return

        async {
//            println("lookupProgressOfflineAsync - lookupProgress")
            lookupProgress(runExecutionId)
        }
    }


    suspend fun lookupProgressOffline() {
        val runExecutionId = store.state().output.outputInfo?.runExecutionId
            ?: return

        lookupProgress(runExecutionId)
    }


    suspend fun lookupProgressActive() {
        val activeInfo = store.state().clientLogicState.logicStatus?.active
            ?: return

        lookupProgress(
            LogicRunExecutionId(
            activeInfo.id, activeInfo.frame.executionId)
        )
    }


    suspend fun lookupProgress(runExecutionId: LogicRunExecutionId) {
        val logicTraceQuery = LogicTraceQuery(LogicTracePath.root)

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val result = progressQuery(runExecutionId.logicRunId, runExecutionId.logicExecutionId, logicTraceQuery)

        when (result) {
            is ClientError ->
                store.update { state -> state
                    .withRun { it.copy(runError = result.message) }
                }

            is ClientSuccess ->
                store.update { state -> state
                    .withRun {
                        it.copy(
                            progress = ReportRunProgress(result.value),
                            runError = null
                        ) }
                }
        }
    }


    private suspend fun progressQuery(
        logicRunId: LogicRunId,
        logicExecutionId: LogicExecutionId,
        logicTraceQuery: LogicTraceQuery
    ): ClientResult<LogicTraceSnapshot> {
        val result = ClientContext.restClient.performDetached(
            LogicConventions.logicTraceStoreLocation,
            CommonRestApi.paramAction to LogicConventions.actionLookup,
            CommonRestApi.paramRunId to logicRunId.value,
            CommonRestApi.paramExecutionId to logicExecutionId.value,
            LogicConventions.paramQuery to logicTraceQuery.asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Map<String, Any>>

                val inputBrowserInfo = LogicTraceSnapshot.ofCollection(resultValue)
                ClientResult.ofSuccess(inputBrowserInfo)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }
}