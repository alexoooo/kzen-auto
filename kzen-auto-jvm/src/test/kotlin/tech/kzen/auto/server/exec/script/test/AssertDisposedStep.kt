package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A test-only [ScriptStep] that asserts, at the point it runs, whether the resource under [key] has already been
 * disposed (recorded into [ResourceDisposalLog]) — throwing (→ terminal failure) on mismatch. Placed in a parent
 * Script *after* the [RunStep][tech.kzen.auto.server.objects.script.step.control.RunStep] that hosts a child which
 * opened the resource, it discriminates ancestor-scoped close policies from self-scoped ones: a self-scoped
 * resource is already disposed by the time the child has settled (so `expectedDisposed: false` would throw), while
 * a `parent`/`run`-scoped resource is still live until the owning ancestor settles.
 */
class AssertDisposedStep(
    private val key: String,
    private val expectedDisposed: Boolean,
    @Suppress("unused") selfLocation: ObjectLocation
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val actual = ResourceDisposalLog.disposed().contains(key)
        check(actual == expectedDisposed) {
            "Resource '$key' disposed=$actual, expected $expectedDisposed"
        }
        return key
    }
}
