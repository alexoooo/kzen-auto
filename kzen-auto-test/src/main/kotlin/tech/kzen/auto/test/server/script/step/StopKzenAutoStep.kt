package tech.kzen.auto.test.server.script.step

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class StopKzenAutoStep(
    private val name: String,
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val stopped = KzenAutoSubprocessRegistry.removeAndClose(name)

        // Dispose-and-forget the registration StartKzenAutoStep provided, so the engine's auto-disposer won't
        // fire a second removeAndClose for this SUT when the owning document settles. Resolves argument-free
        // off this step's `releases: SutContext` — declared as a release rather than a require precisely so
        // the tolerant "nothing to stop" branch below stays reachable instead of being pre-empted by the
        // spine's uniform requires gate.
        execution.releaseContext(qualifier = name)

        if (!stopped) {
            execution.traceDetail("no SUT registered as '$name', nothing to stop")
            return null
        }

        execution.traceDetail("stopped '$name'")
        return null
    }
}
