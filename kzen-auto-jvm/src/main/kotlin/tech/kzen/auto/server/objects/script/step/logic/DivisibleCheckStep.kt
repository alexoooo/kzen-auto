package tech.kzen.auto.server.objects.script.step.logic

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DivisibleCheckStep(
    private val number: ObjectLocation,
    private val divisor: Int,
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.boolean))
    }


    override suspend fun run(execution: StepExecution): Any? {
        val value = execution.referencedValue(number)
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

        execution.traceDetail(result)

        return result
    }
}
