package tech.kzen.auto.server.objects.script.api

import tech.kzen.auto.common.objects.document.logic.BindingSignatureDefiner
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


/**
 * [errorOffset] is a character offset into the expression the step's OWN editor holds, so an editor can mark
 * the failing token. Absent whenever the error has no position within this step's own text: a finding the step
 * derives itself, a diagnostic that landed in generated code, or an error attributed from another object.
 */
data class ScriptStepDefinition(
    val returnValueDefinition: BindingSchema?,
    val validationError: String?,
    val errorOffset: Int? = null
) {
    companion object {
        val empty = of(BindingSchema.empty)

        fun of(returnValueDefinition: BindingSchema): ScriptStepDefinition {
            return ScriptStepDefinition(returnValueDefinition, null)
        }

        fun ofMain(type: TypeMetadata): ScriptStepDefinition = of(BindingSchema.of(
            BindingDefinition(BindingName("main"), BindingSignatureDefiner.contract(type))))
    }
}
