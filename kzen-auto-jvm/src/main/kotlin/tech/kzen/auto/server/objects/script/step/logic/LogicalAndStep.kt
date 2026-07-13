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
class LogicalAndStep(
    private val condition: ObjectLocation,
    private val and: ObjectLocation,
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.boolean))
    }


    override suspend fun run(execution: StepExecution): Any? {
        val conditionValue = execution.referencedValue(condition)
        check(conditionValue is Boolean) {
            "Boolean expected: $condition = $conditionValue"
        }

        val andValue = execution.referencedValue(and)
        check(andValue is Boolean) {
            "Boolean expected: $and = $andValue"
        }

        val result = conditionValue && andValue

        execution.traceDetail(result)

        return result
    }
}
