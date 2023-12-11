package tech.kzen.auto.client.objects.document.sequence.model

import tech.kzen.auto.client.objects.document.sequence.progress.SequenceProgressState
import tech.kzen.auto.client.objects.document.sequence.valid.SequenceValidationState
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.auto.common.objects.document.sequence.model.SequenceTree
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation


data class SequenceState(
    val mainLocation: ObjectLocation,
    val documentNotation: DocumentNotation,
    val sequenceTree: SequenceTree,

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

            if (! SequenceConventions.isSequence(documentNotation)) {
                return null
            }

            return documentPath.toMainObjectLocation()
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