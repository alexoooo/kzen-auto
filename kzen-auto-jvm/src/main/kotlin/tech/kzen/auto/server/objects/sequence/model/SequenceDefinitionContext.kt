package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.auto.common.objects.document.sequence.model.SequenceTree
import tech.kzen.auto.common.objects.document.sequence.model.SequenceValidation


data class SequenceDefinitionContext(
    val sequenceTree: SequenceTree,
    val sequenceValidation: SequenceValidation
) {
}