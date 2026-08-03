package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext


/**
 * A test-only closer, shaped exactly like the production ones: it declares `releases:` rather than
 * `uses:`, resolves its context argument-free off that declaration, and TOLERATES an already-absent
 * resource — a closer's job is to make the absence true, so "already absent" is success. It is therefore
 * never gated by the spine, which is what this step pins.
 */
class ReleaseContextTestStep(
    private val qualifier: String
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val qualifierOrNull = qualifier.ifEmpty { null }
        val existing = execution.contextValueOrNull(qualifier = qualifierOrNull)
        execution.releaseContext(qualifier = qualifierOrNull)

        ContextProbeLog.record("release saw ${existing ?: "nothing"}")
        return existing
    }
}
