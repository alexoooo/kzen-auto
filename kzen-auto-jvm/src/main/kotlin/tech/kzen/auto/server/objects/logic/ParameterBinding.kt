package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.ScriptValueBinding
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect


/**
 * A typed parameter declaration, shared by every Logic flavour with a `parameters` branch (Script and Job).
 * The parameter name is the binding object's own name (e.g. `main.parameters/threshold` is the parameter
 * `threshold`); the declared [type] gives it a real TypeMetadata instead of Any, and `default` (see
 * ParameterDefaultDefiner) is the value used when a run supplies no argument.
 *
 * In a SCRIPT it is exposed to steps by name, fully typed, without a body row (replaces the old
 * ArgumentStep): validated like a step (contributing its type to ScriptValidation) and referenceable, but
 * never executed — the engine resolves its value from the run arguments directly from notation. In a JOB
 * only the notation is read (JobSignatureCapability derives the typed signature; expression compilation
 * exposes the parameter by name) — the [ScriptValueBinding] execution contract is inert there.
 */
@Reflect
class ParameterBinding(
    private val type: TypeMetadata,
    @Suppress("unused") private val default: Any?,
):
    ScriptValueBinding()
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(type)))
    }
}
