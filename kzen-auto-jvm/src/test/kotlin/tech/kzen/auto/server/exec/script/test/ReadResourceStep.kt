package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext


/**
 * A test-only [ScriptStep] that reads the live handle of the resource under [key]
 * ([StepExecution.resource]) and returns it, throwing (→ terminal failure) when no such resource is open —
 * the same "Browser is not open" shape the production Browser action steps have. Placed after a migration
 * barrier it proves an open resource survived the live edit with its value readable in the rebuilt tree
 * (logic-spec §5 "open resources").
 */
class ReadResourceStep(
    private val key: String,
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        return checkNotNull(execution.resource(key)) {
            "Resource '$key' is not open"
        }
    }
}
