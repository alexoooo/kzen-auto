package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition


data class SequenceStepDefinition(
    val returnValueDefinition: TupleDefinition?,
    val validationError: String?
) {
    companion object {
        val empty = of(TupleDefinition.empty)

        fun of(returnValueDefinition: TupleDefinition): SequenceStepDefinition {
            return SequenceStepDefinition(returnValueDefinition, null)
        }
    }
}