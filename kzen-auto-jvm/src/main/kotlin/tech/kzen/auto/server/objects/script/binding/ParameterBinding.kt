package tech.kzen.auto.server.objects.script.binding

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.ScriptValueBinding
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect


/**
 * Exposes a Script parameter to steps by name, fully typed, without a body row (replaces the old
 * ArgumentStep). The parameter name is the binding object's own name (e.g. `main.parameters/threshold`
 * is the parameter `threshold`); the declared [type] gives it a real TypeMetadata instead of Any. Lives
 * in the Script's `parameters` branch, so it is validated (contributing its type to ScriptValidation)
 * and referenceable like a step, but never executed — its value is resolved from the run arguments on
 * demand via [resolveValue].
 */
@Reflect
class ParameterBinding(
    private val type: TypeMetadata,
    private val selfLocation: ObjectLocation
):
    ScriptStep,
    ScriptValueBinding
{
    private val parameterName = TupleComponentName(selfLocation.objectPath.name.value)


    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(type)))
    }


    override fun resolveValue(scriptExecutionContext: ScriptExecutionContext): Any? {
        return scriptExecutionContext.arguments.find(parameterName)
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        return LogicResultSuccess(
            TupleValue.ofMain(resolveValue(scriptExecutionContext)))
    }
}
