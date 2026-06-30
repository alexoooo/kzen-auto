package tech.kzen.auto.server.objects.script.api

import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext


/**
 * A Script step: a notation object addressable by ObjectLocation and validated via [definition] (its type
 * contributes to ScriptValidation, powering in-script typing). Execution is owned by the engine-side step
 * logic in `tech.kzen.auto.server.exec.script`, which dispatches on this object's type and reads its
 * attributes from notation directly — the notation object itself carries no execution behaviour.
 */
interface ScriptStep {
    fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition?
}
