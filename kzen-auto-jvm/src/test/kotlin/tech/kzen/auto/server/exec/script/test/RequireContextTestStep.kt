package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext


/**
 * A test-only consumer: declares `requires` and reads the live value argument-free. It never checks for
 * absence itself — the point is that the spine's uniform gate fails it BEFORE [run] is entered when the
 * context is not provided, so this body only ever sees a live value.
 */
class RequireContextTestStep:
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val value = execution.contextValue()
        ContextProbeLog.record("require saw $value")
        return value
    }
}
