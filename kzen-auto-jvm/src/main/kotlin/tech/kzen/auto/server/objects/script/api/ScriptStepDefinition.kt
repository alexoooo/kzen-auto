package tech.kzen.auto.server.objects.script.api

import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition


data class ScriptStepDefinition(
    val returnValueDefinition: TupleDefinition?,
    val validationError: String?
) {
    companion object {
        val empty = of(TupleDefinition.empty)

        fun of(returnValueDefinition: TupleDefinition): ScriptStepDefinition {
            return ScriptStepDefinition(returnValueDefinition, null)
        }
    }
}