package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy


/**
 * A test-only [ScriptStep] that opens a run-scoped resource under [key] with the [closePolicy] named in notation,
 * registering a closer that records the key into [ResourceDisposalLog]. The "resource" itself is just the key —
 * this step exists to prove [ScriptExtensibilityTest][tech.kzen.auto.server.exec.script.ScriptExtensibilityTest]
 * that [StepExecution.openResource] wires through to the engine's per-node disposal, honouring each
 * [ResourceClosePolicy] when the run settles (Auto/Manual/KeepOnFailure), on both success and failure.
 *
 * [closePolicy] is a plain String parsed here rather than the `ScopedResource` mix-in the production Browser
 * steps use: the mix-in's `SelectValuesEditor` binding drags a JS-only `AttributeEditorManager` reference
 * into this inline test archetype that fails to resolve in the JVM-only test graph. The `openResource` call
 * still receives the real [ResourceClosePolicy] enum, so the disposal wiring under test is identical.
 */
class OpenResourceTestStep(
    private val key: String,
    closePolicy: String,
):
    ScriptStep
{
    private val closePolicy = ResourceClosePolicy.parse(closePolicy)


    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        execution.openResource(key, key, closePolicy) {
            ResourceDisposalLog.record(key)
        }
        return key
    }
}
