package tech.kzen.auto.client.objects.document.sequence.progress

import tech.kzen.auto.client.objects.document.sequence.model.SequenceStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.v1.model.LogicConventions
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId


class SequenceProgressStore(
    private val sequenceStore: SequenceStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun init() {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = mostRecentQuery()

        when (result) {
            is ClientError ->
                sequenceStore.update { state -> state
                    .withGlobalError(result.message)
                }

            is ClientSuccess ->
                sequenceStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            mostRecentTrace = result.value
                        )
                    }
                }
        }
    }


    private suspend fun mostRecentQuery(): ClientResult<SequenceProgressState.MostRecentResult> {
        val mainLocation = sequenceStore.mainLocation()

        val result = ClientContext.restClient.performDetached(
            LogicConventions.logicTraceStoreLocation,
            LogicConventions.paramAction to LogicConventions.actionMostRecent,
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
}