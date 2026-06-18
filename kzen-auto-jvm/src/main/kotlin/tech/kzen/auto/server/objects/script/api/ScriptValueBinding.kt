package tech.kzen.auto.server.objects.script.api

import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext


/**
 * A named, typed value injected into the Script step namespace by a container (the Script header for
 * parameters; a MappingStep for its loop item) rather than produced by an executed body step. A binding
 * is a real addressable ScriptStep object — so it is validated (carries a type) and referenced by
 * ObjectLocation like any step — but it is NOT in a `steps` branch, so it never executes. Its value is
 * therefore resolved on demand at reference time (see [ScriptExecutionContext.referencedValue]) instead
 * of being read from a populated step model.
 */
interface ScriptValueBinding {
    fun resolveValue(scriptExecutionContext: ScriptExecutionContext): Any?
}
