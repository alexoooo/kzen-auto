package tech.kzen.auto.client.objects.document.common.run

import kotlinx.coroutines.delay
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunResponse
import tech.kzen.lib.common.model.locate.ObjectLocation


class ExecutionRunStore(
    private val executionRunState: () -> ExecutionRunState,
    private val mainLocation: () -> ObjectLocation,
    private val setExecutionRunState: ((ExecutionRunState) -> ExecutionRunState) -> Unit,
    private val refreshCallback: (Boolean) -> Unit
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun open() {
        lookupStatus()
        refreshCallback(false)

//        val activeInfo = executionRunState().logicStatus?.active
//        if (activeInfo != null) {
//            lookupProgress(LogicRunExecutionId(
//                activeInfo.id, activeInfo.frame.executionId))
//        }
//        else {
//            lookupProgressOffline()
//        }
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
//        lookupProgressActive()
        refreshCallback(false)
    }


    suspend fun lookupStatus() {
        val status = ClientContext.restClient.logicStatus()

        val active = status.active
        val otherRunning = active != null && active.frame.objectLocation != mainLocation()

        setExecutionRunState {
            if (otherRunning) {
                it.copy(
                    logicStatus = status.copy(active = null),
                    otherRunning = true)
            }
            else {
                it.copy(
                    logicStatus = status,
                    otherRunning = false)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startAndRunAsync() {
        setExecutionRunState {
            it.copy(
                starting = true,
                runError = null)
        }

        async {
            delay(1)
            val logicRunId = ClientContext.restClient.logicStart(
                mainLocation())

            if (logicRunId == null) {
                setExecutionRunState {
                    it.copy(
                        starting = false,
                        runError = "Unable to start")
                }
            }
            else {
                delay(10)
                setExecutionRunState {
                    it.copy(starting = false)
                }

                refreshAll()
            }
        }
    }


    private suspend fun refreshAll() {
//        delay(10)
        lookupStatus()

//        delay(10)
        refreshCallback(true)
//        executionRunState.output.lookupOutputWithFallback()
//
//        executionRunState.previewFiltered.lookupSummaryWithFallbackAsync()
//        lookupProgressWithFallbackAsync()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun cancelAsync() {
        val logicRunId = executionRunState().logicStatus?.active?.id
            ?: return

        setExecutionRunState {
            it.copy(
                cancelling = true,
                runError = null
            )
        }

        async {
            delay(1)
            val response = ClientContext.restClient.logicCancel(logicRunId)

            if (response != LogicRunResponse.Submitted) {
                setExecutionRunState {
                    it.copy(
                        cancelling = false,
                        runError = "Unable to cancel")
                }
            }
            else {
                delay(10)
                setExecutionRunState {
                    it.copy(cancelling = false)
                }

                refreshAll()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun lookupProgressWithFallbackAsync() {
//        if (executionRunState.state().isRunning()) {
//            lookupProgressActiveAsync()
//        }
//        else {
//            lookupProgressOfflineAsync()
//        }
//    }


//    fun lookupProgressOfflineAsync() {
//        val runExecutionId = executionRunState.state().output.outputInfo?.runExecutionId
//            ?: return
//
//        async {
//            lookupProgress(runExecutionId)
//        }
//    }


//    suspend fun lookupProgressOffline() {
//        val runExecutionId = executionRunState().output.outputInfo?.runExecutionId
//            ?: return
//
//        lookupProgress(runExecutionId)
//    }


//    fun lookupProgressActiveAsync() {
//        val activeInfo = executionRunState().logicStatus?.active
//            ?: return
//
//        async {
//            lookupProgress(LogicRunExecutionId(
//                activeInfo.id, activeInfo.frame.executionId))
//        }
//    }


//    suspend fun lookupProgressActive() {
//        val activeInfo = executionRunState().logicStatus?.active
//            ?: return
//
//        lookupProgress(LogicRunExecutionId(
//            activeInfo.id, activeInfo.frame.executionId))
//    }


//    suspend fun lookupProgress(runExecutionId: LogicRunExecutionId) {
//        val logicTraceQuery = LogicTraceQuery(LogicTracePath.root)
//
//        @Suppress("MoveVariableDeclarationIntoWhen")
//        val result = progressQuery(runExecutionId.logicRunId, runExecutionId.logicExecutionId, logicTraceQuery)
//
//        when (result) {
//            is ClientError ->
//                setExecutionRunState {
//                    it.copy(runError = result.message)
//                }
//
//            is ClientSuccess ->
//                setExecutionRunState {
//                    it.copy(
//                        progress = result.value,
//                        runError = null)
//                }
//        }
//    }
//
//
//    private suspend fun progressQuery(
//        logicRunId: LogicRunId,
//        logicExecutionId: LogicExecutionId,
//        logicTraceQuery: LogicTraceQuery
//    ): ClientResult<LogicTraceSnapshot> {
//        val result = ClientContext.restClient.performDetached(
//            LogicConventions.logicTraceStoreLocation,
//            LogicConventions.runIdKey to logicRunId.value,
//            LogicConventions.executionIdKey to logicExecutionId.value,
//            LogicConventions.queryKey to logicTraceQuery.asString()
//        )
//
//        return when (result) {
//            is ExecutionSuccess -> {
//                @Suppress("UNCHECKED_CAST")
//                val resultValue = result.value.get() as Map<String, Map<String, Any>>
//
//                val inputBrowserInfo = LogicTraceSnapshot.ofCollection(resultValue)
//                ClientResult.ofSuccess(inputBrowserInfo)
//            }
//
//            is ExecutionFailure ->
//                ClientResult.ofError(result.errorMessage)
//        }
//    }
}