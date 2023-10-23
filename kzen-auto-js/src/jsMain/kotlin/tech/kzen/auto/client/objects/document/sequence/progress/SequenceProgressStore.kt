package tech.kzen.auto.client.objects.document.sequence.progress

import tech.kzen.auto.client.objects.document.sequence.model.SequenceStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.v1.model.LogicConventions
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceQuery
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot


class SequenceProgressStore(
    private val sequenceStore: SequenceStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        val logicRunExecutionId = mostRecent()
        if (logicRunExecutionId == null) {
            sequenceStore.update { state -> state
                .withProgressSuccess {
                    it.copy(
                        logicTraceSnapshot = null,
                        loaded = true
                    )
                }
            }
            return
        }

        val logicTraceQuery = LogicTraceQuery(LogicTracePath.root)

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val progressResult = progressQuery(
            logicRunExecutionId.logicRunId, logicRunExecutionId.logicExecutionId, logicTraceQuery)

        when (progressResult) {
            is ClientError -> {
                sequenceStore.update { state -> state
                    .withGlobalError(progressResult.message)
                    .withProgressSuccess {
                        it.copy(
                            logicTraceSnapshot = null,
                            loaded = true
                        )
                    }
                }
            }

            is ClientSuccess -> {
                sequenceStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            logicTraceSnapshot = progressResult.value,
                            loaded = true
                        )
                    }
                }
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


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun mostRecent(): LogicRunExecutionId? {
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val mostRecentResult = mostRecentQuery()

        return when (mostRecentResult) {
            is ClientError -> {
                sequenceStore.update { state -> state
                    .withGlobalError(mostRecentResult.message)
                }
                null
            }

            is ClientSuccess -> {
                sequenceStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            logicRunExecutionId = mostRecentResult.value.logicRunExecutionId
                        )
                    }
                }

                mostRecentResult.value.logicRunExecutionId
            }
        }
    }


    private suspend fun mostRecentQuery(): ClientResult<SequenceProgressState.MostRecentResult> {
        val mainLocation = sequenceStore.mainLocation()

        val result = ClientContext.restClient.performDetached(
            LogicConventions.logicTraceStoreLocation,
            CommonRestApi.paramAction to LogicConventions.actionMostRecent,
            LogicConventions.paramSubDocumentPath to mainLocation.documentPath.asString(),
            LogicConventions.paramSubObjectPath to mainLocation.objectPath.asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultCollection = result.value.get() as Map<String, String>?

                val resultValue = resultCollection?.let { LogicRunExecutionId.ofCollection(it) }
                ClientResult.ofSuccess(SequenceProgressState.MostRecentResult(resultValue))
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun clear() {
        val logicRunExecutionId = mostRecent()
        if (logicRunExecutionId == null) {
            sequenceStore.update { state -> state
                .withProgressSuccess {
                    it.copy(
                        logicRunExecutionId = null,
                        logicTraceSnapshot = null)
                }
            }
            return
        }

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val clearResult = clearCommand()

        when (clearResult) {
            is ClientError -> {
                sequenceStore.update { state -> state
                    .withGlobalError(clearResult.message)
                }
            }

            is ClientSuccess -> {
                sequenceStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            logicRunExecutionId = null,
                            logicTraceSnapshot = null)
                    }
                }
            }
        }
    }

//
//    private suspend fun mostRecent(): LogicRunExecutionId? {
//        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
//        val mostRecentResult = mostRecentQuery()
//
//        return when (mostRecentResult) {
//            is ClientError -> {
//                sequenceStore.update { state -> state
//                    .withGlobalError(mostRecentResult.message)
//                }
//                null
//            }
//
//            is ClientSuccess -> {
//                sequenceStore.update { state -> state
//                    .withProgressSuccess {
//                        it.copy(
//                            logicRunExecutionId = mostRecentResult.value.logicRunExecutionId
//                        )
//                    }
//                }
//
//                mostRecentResult.value.logicRunExecutionId
//            }
//        }
//    }


    private suspend fun clearCommand(): ClientResult<Boolean> {
        val mainLocation = sequenceStore.mainLocation()

        val result = ClientContext.restClient.performDetached(
            LogicConventions.logicTraceStoreLocation,
            CommonRestApi.paramAction to LogicConventions.actionReset,
            LogicConventions.paramSubDocumentPath to mainLocation.documentPath.asString(),
            LogicConventions.paramSubObjectPath to mainLocation.objectPath.asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                val resultValue = result.value.get() as Boolean
                ClientResult.ofSuccess(resultValue)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }
}