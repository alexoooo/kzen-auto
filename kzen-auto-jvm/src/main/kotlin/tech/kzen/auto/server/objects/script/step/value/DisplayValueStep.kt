package tech.kzen.auto.server.objects.script.step.value

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DisplayValueStep(
    private val text: ObjectLocation,
    selfLocation: ObjectLocation
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        val value = scriptExecutionContext.referencedValue(text)

        val text = value?.toString() ?: "<null>"
        val executionValue = TextExecutionValue(text)

        traceDetail(scriptExecutionContext, executionValue)

        return LogicResultSuccess(TupleValue.ofMain(text))
    }
}