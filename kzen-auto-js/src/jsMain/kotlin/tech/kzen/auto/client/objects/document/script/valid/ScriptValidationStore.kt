package tech.kzen.auto.client.objects.document.script.valid

import tech.kzen.auto.client.objects.document.common.valid.ServerValidationFetch
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation


class ScriptValidationStore(
    private val scriptStore: ScriptStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh(isCurrent: () -> Boolean) {
        scriptStore.updateValidation {
            it.copy(loaded = false)
        }

        val mainLocation = scriptStore.mainLocation()

        val outcome = ServerValidationFetch.fetch(
            isCurrent = isCurrent,
            perform = {
                scriptStore.restClient.performDetached(
                    ScriptConventions.scriptValidatorLocation,
                    CommonRestApi.paramHostDocumentPath to mainLocation.documentPath.asString())
            },
            parse = { ScriptValidation.ofExecutionValue(it) })

        when (outcome) {
            is ServerValidationFetch.Outcome.Current ->
                scriptStore.updateValidation {
                    it.copy(
                        scriptValidation = outcome.value,
                        loaded = true)
                }

            is ServerValidationFetch.Outcome.Failed ->
                scriptStore.update { state -> state
                    .withGlobalError(outcome.errorMessage)
                    .withValidation {
                        it.copy(
                            scriptValidation = null,
                            loaded = true
                        )
                    }
                }

            ServerValidationFetch.Outcome.Superseded -> {}
        }
    }
}
