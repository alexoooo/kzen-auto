package tech.kzen.auto.client.objects.document.sequence.valid

import tech.kzen.auto.client.objects.document.sequence.model.SequenceStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.auto.common.objects.document.sequence.model.SequenceValidation
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation


class SequenceValidationStore(
    private val sequenceStore: SequenceStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        sequenceStore.updateValidation {
            it.copy(loaded = false)
        }

        val mainLocation = sequenceStore.mainLocation()

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val result = validationQuery(mainLocation)

        when (result) {
            is ClientSuccess ->
                sequenceStore.updateValidation {
                    it.copy(
                        sequenceValidation = result.value,
                        loaded = true)
                }

            is ClientError ->
                sequenceStore.update { state -> state
                    .withGlobalError(result.message)
                    .withValidation {
                        it.copy(
                            sequenceValidation = null,
                            loaded = true
                        )
                    }
                }
        }
    }


    private suspend fun validationQuery(
        mainLocation: ObjectLocation
    ):
        ClientResult<SequenceValidation>
    {
        val result = ClientContext.restClient.performDetached(
            SequenceConventions.sequenceValidatorLocation,
            CommonRestApi.paramHostDocumentPath to mainLocation.documentPath.asString())

        return when (result) {
            is ExecutionSuccess -> {
                val sequenceValidation = SequenceValidation.ofExecutionValue(
                    result.value as MapExecutionValue)

                ClientResult.ofSuccess(sequenceValidation)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }
}