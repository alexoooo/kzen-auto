package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.auto.client.objects.document.script.progress.ScriptProgressState
import tech.kzen.auto.client.objects.document.script.valid.ScriptValidationState
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation


data class ScriptState(
    val mainLocation: ObjectLocation,
    val documentNotation: DocumentNotation,
    val scriptTree: ScriptTree,

    val progress: ScriptProgressState = ScriptProgressState(),
    val validationState: ScriptValidationState = ScriptValidationState(),

    val globalError: String? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun tryMainLocation(clientState: ClientState): ObjectLocation? {
            val documentPath = clientState
                .navigationRoute
                .documentPath
                ?: return null

            val documentNotation = clientState
                .graphStructure()
                .graphNotation
                .documents[documentPath]
                ?: return null

            if (!ScriptConventions.isScript(documentNotation)) {
                return null
            }

            return documentPath.toMainObjectLocation()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withGlobalError(globalError: String): ScriptState {
        return copy(
            globalError = globalError)
    }


    fun withProgressSuccess(updater: (ScriptProgressState) -> ScriptProgressState): ScriptState {
        return copy(
            progress = updater(progress),
            globalError = null)
    }


    fun withValidation(updater: (ScriptValidationState) -> ScriptValidationState): ScriptState {
        return copy(
            validationState = updater(validationState),
            globalError = null)
    }
}