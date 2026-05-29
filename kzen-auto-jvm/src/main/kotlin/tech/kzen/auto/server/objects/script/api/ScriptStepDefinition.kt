package tech.kzen.auto.server.objects.script.api

import tech.kzen.lib.common.exec.tuple.TupleDefinition


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