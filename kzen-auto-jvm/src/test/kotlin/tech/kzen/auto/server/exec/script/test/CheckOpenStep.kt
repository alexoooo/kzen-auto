package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.job.worker.test.CloseCountingResource
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect


/**
 * A test-only [ScriptStep] that reads the referenced [input] as a [CloseCountingResource] that is still open,
 * throwing (→ terminal failure) otherwise, and returns its name. Reading goes through the value graph, so a
 * value whose resource the run has already closed fails by name there before this step ever sees it.
 */
@Reflect
class CheckOpenStep(
    private val input: ObjectLocation
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.ofMain(TypeMetadata.string)
    }


    override suspend fun run(execution: StepExecution): Any? {
        val value = execution.referencedValue(input)
        check(value is CloseCountingResource) { "Not a CloseCountingResource: $value" }
        check(!value.isClosed) { "Already closed: $value" }
        return value.name
    }
}
