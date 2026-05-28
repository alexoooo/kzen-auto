package tech.kzen.auto.server.objects.script.step.logic

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DivisibleCheckStep(
    private val number: ObjectLocation,
    private val divisor: Int,
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
        val step = scriptExecutionContext.stepModel(number)

        val value = step?.value?.mainComponentValue()
        check(value is Number) {
            "Number expected: $number = $value"
        }

        val intValue = value.toInt()
        check(intValue.toDouble() == value.toDouble()) {
            "Integer expected: $number = $value"
        }

        require(divisor > 0) {
            "Positive divisor required: $divisor"
        }

        val result = intValue % divisor == 0

        traceDetail(scriptExecutionContext, result)

        return LogicResultSuccess(
            TupleValue.ofMain(result))
    }
}