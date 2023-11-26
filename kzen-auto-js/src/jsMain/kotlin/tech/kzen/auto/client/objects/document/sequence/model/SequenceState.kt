package tech.kzen.auto.client.objects.document.sequence.model

import tech.kzen.auto.client.objects.document.sequence.progress.SequenceProgressState
import tech.kzen.auto.client.objects.document.sequence.valid.SequenceValidationState
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


data class SequenceState(
    val mainLocation: ObjectLocation,
//    val mainDefinition: ObjectDefinition,

    val progress: SequenceProgressState = SequenceProgressState(),
    val validationState: SequenceValidationState = SequenceValidationState(),

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

            if (! isSequence(documentNotation)) {
                return null
            }

            return documentPath.toMainObjectLocation()
        }


        fun isSequence(documentNotation: DocumentNotation): Boolean {
            val mainObjectNotation =
                documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

            val mainObjectIs =
                mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

            return mainObjectIs == SequenceConventions.objectName.value
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withGlobalError(globalError: String): SequenceState {
        return copy(
            globalError = globalError)
    }


    fun withProgressSuccess(updater: (SequenceProgressState) -> SequenceProgressState): SequenceState {
        return copy(
            progress = updater(progress),
            globalError = null)
    }


    fun withValidation(updater: (SequenceValidationState) -> SequenceValidationState): SequenceState {
        return copy(
            validationState = updater(validationState),
            globalError = null)
    }
}