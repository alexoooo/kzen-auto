package tech.kzen.auto.client.objects.document.sequence.valid

import tech.kzen.auto.common.paradigm.sequence.SequenceValidation


data class SequenceValidationState(
    val loaded: Boolean = false,
    val sequenceValidation: SequenceValidation? = null
)