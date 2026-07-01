package tech.kzen.auto.server.objects.script.step.value

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DisplayValueStep(
    private val text: ObjectLocation,
    @Suppress("unused") selfLocation: ObjectLocation
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val value = execution.referencedValue(text)

        val display = value?.toString() ?: "<null>"
        execution.traceDetail(TextExecutionValue(display))

        return display
    }
}
