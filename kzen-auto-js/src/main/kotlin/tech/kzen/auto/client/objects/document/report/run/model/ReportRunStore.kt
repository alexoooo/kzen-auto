package tech.kzen.auto.client.objects.document.report.run.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.v1.model.*
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceQuery
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot


class ReportRunStore(
    private val store: ReportStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun init() {
        lookupStatus()

        val activeInfo = store.state().run.logicStatus?.active
        if (activeInfo != null) {
            lookupProgress(LogicRunExecutionId(
                activeInfo.id, activeInfo.frame.executionId))
        }
        else {
            lookupProgressOffline()
        }
    }


//    suspend fun lookupProgressOffline() {
//        val runExecutionId = store.state().output.outputInfo?.runExecutionId
//            ?: return
//
//        lookupProgress(runExecutionId)
//    }
//
//
//    suspend fun lookupProgressActive() {
//        val activeInfo = store.state().run.logicStatus?.active
//            ?: return
//
//        lookupProgress(LogicRunExecutionId(
//            activeInfo.id, activeInfo.frame.executionId))
//    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        lookupStatus()
        lookupProgressActive()

//        if (store.state().run.logicStatus?.active != null &&
//                store.state().output.outputInfo == null
//        ) {
//            console.log("Refresh with outputInfo = null")
//            store.output.lookupOutputWithFallback()
//        }
    }


    suspend fun lookupStatus() {
        val status = ClientContext.restClient.logicStatus()

        store.update { state -> state
            .withRun { it.copy(logicStatus = status) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startAndRunAsync() {
        store.update { state -> state
            .withRun { it.copy(
                starting = true,
                runError = null
            ) }
        }

        async {
            delay(1)
            val logicRunId = ClientContext.restClient.logicStart(
                store.mainLocation())

            if (logicRunId == null) {
                store.update { state -> state
                    .withRun { it.copy(
                        starting = false,
                        runError = "Unable to start"
                    ) }
                }
            }
            else {
                delay(10)
                store.update { state -> state
                    .withRun { it.copy(starting = false) }
                }

                refreshAll()
            }
        }
    }


    private suspend fun refreshAll() {
        delay(10)
        lookupStatus()

        delay(10)
        store.output.lookupOutputWithFallback()

        delay(10)
        store.previewFiltered.lookupSummaryWithFallback()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun cancelAsync() {
        val logicRunId = store.state().run.logicStatus?.active?.id
            ?: return

        store.update { state -> state
            .withRun { it.copy(
                cancelling = true,
                runError = null
            ) }
        }

        async {
            delay(1)
            val response = ClientContext.restClient.logicCancel(logicRunId)

            if (response != LogicRunResponse.Submitted) {
                store.update { state -> state
                    .withRun { it.copy(
                        cancelling = false,
                        runError = "Unable to cancel"
                    ) }
                }
            }
            else {
                delay(10)
                store.update { state -> state
                    .withRun { it.copy(cancelling = false) }
                }

                refreshAll()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun lookupProgressOfflineAsync() {
        async {
            lookupProgressOffline()
        }
    }


    suspend fun lookupProgressOffline() {
        val runExecutionId = store.state().output.outputInfo?.runExecutionId
            ?: return

        lookupProgress(runExecutionId)
    }


    suspend fun lookupProgressActive() {
        val activeInfo = store.state().run.logicStatus?.active
            ?: return

        lookupProgress(LogicRunExecutionId(
            activeInfo.id, activeInfo.frame.executionId))
    }


    suspend fun lookupProgress(runExecutionId: LogicRunExecutionId) {
        val logicTraceQuery = LogicTraceQuery(LogicTracePath.root)

        @Suppress("MoveVariableDeclarationIntoWhen")
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
            LogicConventions.runIdKey to logicRunId.value,
            LogicConventions.executionIdKey to logicExecutionId.value,
            LogicConventions.queryKey to logicTraceQuery.asString()
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