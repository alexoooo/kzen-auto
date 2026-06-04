package tech.kzen.auto.client.objects.document.script.valid

import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation


class ScriptValidationStore(
    private val scriptStore: ScriptStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        scriptStore.updateValidation {
            it.copy(loaded = false)
        }

        val mainLocation = scriptStore.mainLocation()

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val result = validationQuery(mainLocation)

        when (result) {
            is ClientSuccess ->
                scriptStore.updateValidation {
                    it.copy(
                        scriptValidation = result.value,
                        loaded = true)
                }

            is ClientError ->
                scriptStore.update { state -> state
                    .withGlobalError(result.message)
                    .withValidation {
                        it.copy(
                            scriptValidation = null,
                            loaded = true
                        )
                    }
                }
        }
    }


    private suspend fun validationQuery(
        mainLocation: ObjectLocation
    ):
        ClientResult<ScriptValidation>
    {
        val result = scriptStore.restClient.performDetached(
            ScriptConventions.scriptValidatorLocation,
            CommonRestApi.paramHostDocumentPath to mainLocation.documentPath.asString())

        return when (result) {
            is ExecutionSuccess -> {
                val scriptValidation = ScriptValidation.ofExecutionValue(
                    result.value as MapExecutionValue)

                ClientResult.ofSuccess(scriptValidation)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }
}