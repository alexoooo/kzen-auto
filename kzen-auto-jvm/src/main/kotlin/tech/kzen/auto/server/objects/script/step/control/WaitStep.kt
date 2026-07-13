package tech.kzen.auto.server.objects.script.step.control

import kotlinx.coroutines.delay
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class WaitStep(
    private val milliseconds: Long,
):
    ScriptStep
{
    /** Pause for a fixed duration via a coroutine [delay] — no engine thread is blocked while waiting. */
    override suspend fun run(execution: StepExecution): Any? {
        delay(milliseconds)
        return null
    }


    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }
}
