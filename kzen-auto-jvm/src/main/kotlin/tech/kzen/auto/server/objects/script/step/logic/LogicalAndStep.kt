package tech.kzen.auto.server.objects.script.step.logic

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class LogicalAndStep(
    private val condition: ObjectLocation,
    private val and: ObjectLocation,
    selfLocation: ObjectLocation
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.boolean))
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        val conditionStep = scriptExecutionContext.stepModel(condition)
        val conditionValue = conditionStep?.value?.mainComponentValue()
        check(conditionValue is Boolean) {
            "Boolean expected: $condition = $conditionValue"
        }

        val andStep = scriptExecutionContext.stepModel(and)
        val andValue = andStep?.value?.mainComponentValue()
        check(andValue is Boolean) {
            "Boolean expected: $and = $andValue"
        }

        val result = conditionValue && andValue

        traceDetail(scriptExecutionContext, result)

        return LogicResultSuccess(
            TupleValue.ofMain(result))
    }
}