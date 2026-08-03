package tech.kzen.auto.test.server.script.step

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
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
        // This step decides WHEN the SUT dies, never HOW: StartKzenAutoStep attached the identity-checked
        // teardown to the binding, and releasing the binding claims and runs that closer exactly once —
        // eagerly here instead of at the owning document's settle. Tearing the process down here as well
        // would be a second path to the same end, correct only by virtue of the closer's identity check.
        //
        // Resolves the binding by qualifier off this step's `releases: SutContext` — declared as a release
        // rather than a use precisely so the tolerant "nothing to stop" branch below stays reachable instead
        // of being pre-empted by the spine's uniform `uses` gate.
        val registered = execution.contextValueOrNull(qualifier = name) != null

        // Killing a subprocess and deleting its temp dir blocks for real, and the closer runs on the calling
        // thread — so it belongs off the engine's dispatcher, exactly as the browser closer does.
        execution.blocking {
            execution.releaseContext(qualifier = name)
        }

        if (! registered) {
            execution.traceDetail("no SUT registered as '$name', nothing to stop")
            return null
        }

        execution.traceDetail("stopped '$name'")
        return null
    }
}
