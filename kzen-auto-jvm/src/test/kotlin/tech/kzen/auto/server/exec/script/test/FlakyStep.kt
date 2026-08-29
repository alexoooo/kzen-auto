package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.location.ObjectLocation
import java.util.concurrent.atomic.AtomicInteger


/**
 * A test-only [ScriptStep] that fails its FIRST invocation (globally) and succeeds thereafter, returning its
 * referenced [input] — a transient failure that clears on retry. Drives
 * [tech.kzen.auto.server.exec.job.JobRunWorkerTest]'s pause-on-error path: a [RunWorker]'s hosted child fails,
 * the whole Job parks Suspended(Error), and a plain resume re-runs the recoverable step to success (no edit
 * needed), proving the engine's central pause-without-unwind reaches a nested-Logic Worker's child.
 *
 * Fail-once is keyed to a static counter (like the gated test Workers), so [reset] it before each test.
 * Registered by hand via [ScriptStepTestModule] (no `@Reflect` / KSP in the test source set).
 */
class FlakyStep(
    private val input: ObjectLocation,
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val attempts = AtomicInteger(0)

        fun reset() {
            attempts.set(0)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.ofMain(TypeMetadata.any)
    }


    override suspend fun run(execution: StepExecution): Any? {
        if (attempts.getAndIncrement() == 0) {
            throw IllegalStateException("flaky failure")
        }
        return execution.referencedValue(input)
    }
}
