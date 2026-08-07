package tech.kzen.auto.server.objects.script.api

import tech.kzen.lib.common.exec.tuple.TupleDefinition


/**
 * [errorOffset] is a character offset into the expression the step's OWN editor holds, so an editor can mark
 * the failing token. Absent whenever the error has no position within this step's own text: a finding the step
 * derives itself, a diagnostic that landed in generated code, or an error attributed from another object.
 */
data class ScriptStepDefinition(
    val returnValueDefinition: TupleDefinition?,
    val validationError: String?,
    val errorOffset: Int? = null
) {
    companion object {
        val empty = of(TupleDefinition.empty)

        fun of(returnValueDefinition: TupleDefinition): ScriptStepDefinition {
            return ScriptStepDefinition(returnValueDefinition, null)
        }
    }
}