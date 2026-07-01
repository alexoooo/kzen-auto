package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A test-only [ScriptStep] that always throws, driving the run to a terminal [Outcome.Failed][tech.kzen.lib.common.exec.engine.Outcome.Failed]
 * (pause-on-error is off by default, so the throw propagates rather than parking). Used by
 * [ScriptExtensibilityTest][tech.kzen.auto.server.exec.script.ScriptExtensibilityTest] to check the failure
 * branch of resource disposal — KeepOnFailure resources are retained on a failed run, Auto disposed.
 */
class FailStep(
    private val message: String,
    @Suppress("unused") selfLocation: ObjectLocation
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        throw IllegalStateException(message)
    }
}
