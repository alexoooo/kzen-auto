package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class PauseStep:
    ScriptStep
{
    /**
     * Explicit breakpoint: suspend this node until the run is resumed. Re-running it (e.g. inside a loop) pauses
     * again for free — there is no `resumed` flag to reset, because the suspension is the coroutine re-reaching
     * this call, not a remembered boolean.
     */
    override suspend fun run(execution: StepExecution): Any? {
        execution.pauseHere()
        return null
    }


    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }
}
