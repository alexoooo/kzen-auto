package tech.kzen.auto.server.objects.script.step.value

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class NumberLiteralStep(
    private val value: Double,
    @Suppress("unused") private val selfLocation: ObjectLocation
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun run(execution: StepExecution): Any? {
        return value
    }


    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.double))
    }
}
