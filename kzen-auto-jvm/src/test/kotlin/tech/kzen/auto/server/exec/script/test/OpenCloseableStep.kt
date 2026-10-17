package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.job.worker.test.CloseCountingResource
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassName


/**
 * A test-only [ScriptStep] whose value is a fresh `AutoCloseable` ([CloseCountingResource], named [name] plus the
 * count opened so far) and nothing else: no `openResource`, no context binding. What happens to that value is
 * then entirely the Script's recording of it ([tech.kzen.auto.server.exec.script.ScriptRunContext.recordValue]):
 * held by the frame, live for later steps, closed when superseded or when the frame settles.
 */
@Reflect
class OpenCloseableStep(
    private val name: String
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.ofMain(TypeMetadata(
            ClassName(CloseCountingResource::class.java.name), listOf(), false))
    }


    override suspend fun run(execution: StepExecution): Any? {
        return CloseCountingResource("$name-${CloseCountingResource.opened.get() + 1}")
    }
}
