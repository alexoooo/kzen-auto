package tech.kzen.auto.server.objects.script.step.value

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.reflect.Reflect


/**
 * Deprecated (superseded by rowless [tech.kzen.auto.server.objects.script.binding.ParameterBinding]), but still
 * executable so legacy scripts that read a named argument as a body row keep working: yields the run argument
 * named [parameter] (the value a hosting RunStep passed in).
 */
@Reflect
class ArgumentStep(
    parameter: String,
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    private val tupleComponentName = TupleComponentName(parameter)


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.any))
    }


    override suspend fun run(execution: StepExecution): Any? {
        return execution.argument(tupleComponentName)
    }
}
