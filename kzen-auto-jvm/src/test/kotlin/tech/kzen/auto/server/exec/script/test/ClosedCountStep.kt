package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.job.worker.test.CloseCountingResource
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect


/**
 * A test-only [ScriptStep] returning how many [CloseCountingResource]s have closed so far. As a loop body's
 * terminal step after an [OpenCloseableStep], the collected counts say at which iteration each previous
 * iteration's value closed.
 */
@Reflect
class ClosedCountStep: ScriptStep {
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.ofMain(TypeMetadata.int)
    }


    override suspend fun run(execution: StepExecution): Any? {
        return CloseCountingResource.closed.get()
    }
}
