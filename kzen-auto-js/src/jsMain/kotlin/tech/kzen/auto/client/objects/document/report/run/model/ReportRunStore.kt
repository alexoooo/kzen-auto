package tech.kzen.auto.client.objects.document.report.run.model

import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot


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
        val result = progressQuery(runExecutionId.logicRunId, logicTraceQuery)

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


    // Query the whole-run trace merge (by run id), not the exact (run, execution) buffer: the engine runs a
    // Report on a single root node whose execution id is the engine's, not the controller's trace-buffer
    // execution id, so an exact lookup misses — the run-merge is the right query for a single-node run (and is
    // how ScriptProgressStore falls back).
    private suspend fun progressQuery(
        logicRunId: LogicRunId,
        logicTraceQuery: LogicTraceQuery
    ): ClientResult<LogicTraceSnapshot> {
        val result = store.restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionLookupRun,
            CommonRestApi.paramRunId to logicRunId.value,
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