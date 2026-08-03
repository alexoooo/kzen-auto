package tech.kzen.auto.server.objects.script.step.browser

import kotlinx.coroutines.delay
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BrowserCloseStep(
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        delay(250)

        // Explicit close: name the Context to release, and the engine runs the disposal its opening step
        // attached, at most once — which is what a Manual closePolicy relies on. The division of labour is
        // deliberate: only the binder knows how its own handle dies, so a closer that quit the driver itself
        // would duplicate that knowledge and tear the browser down twice. Deliberately TOLERANT of an
        // already-absent browser — a closer's job is to make the absence true, which is why this step declares
        // `releases: BrowserContext` (never gated by the spine) rather than `uses`. Offloaded because the
        // disposal it triggers is a blocking Selenium quit.
        execution.blocking { execution.releaseContext() }

        execution.traceDetail("Browser closed")

        return null
    }
}
