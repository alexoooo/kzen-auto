package tech.kzen.auto.client.objects.document.script.valid

import tech.kzen.auto.common.objects.document.script.model.ScriptValidation


data class ScriptValidationState(
    val loaded: Boolean = false,
    val scriptValidation: ScriptValidation? = null
)