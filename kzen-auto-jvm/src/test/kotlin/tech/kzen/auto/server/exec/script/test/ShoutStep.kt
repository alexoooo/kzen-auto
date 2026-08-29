package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A test-only third-party [ScriptStep]: uppercases the referenced value and appends "!!!". It exists ONLY in the
 * test source set (no `@Reflect`; registered by hand via [ScriptStepTestModule]) and is entirely unknown to any
 * kzen source — proving [tech.kzen.auto.server.exec.script.ScriptExtensibilityTest] that a new step type runs
 * end-to-end on the engine with no edit to [tech.kzen.auto.server.exec.script.ScriptLogicCompiler] or any kzen
 * dispatch, exactly as a real plugin would add one.
 */
class ShoutStep(
    private val input: ObjectLocation,
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.ofMain(TypeMetadata.string)
    }


    override suspend fun run(execution: StepExecution): Any? {
        val value = execution.referencedValue(input)?.toString() ?: ""
        val shouted = value.uppercase() + "!!!"
        execution.traceDetail(shouted)
        return shouted
    }
}
