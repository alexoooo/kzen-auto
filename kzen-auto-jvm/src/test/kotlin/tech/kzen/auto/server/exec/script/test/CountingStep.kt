package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import java.util.concurrent.atomic.AtomicInteger


/**
 * A test-only side-effect counter [ScriptStep]: each invocation increments a process-global counter and returns
 * the running count (an Int). Placed inside a loop body it makes iteration re-execution observable — the
 * mid-loop migration-resume tests assert the count to prove completed iterations were NOT re-run across a
 * pause -> edit -> resume (and that a fallback restart DID re-run them). [reset] in each test's setup.
 */
class CountingStep: ScriptStep {
    companion object {
        val count = AtomicInteger(0)

        fun reset() {
            count.set(0)
        }
    }


    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(TypeMetadata.int)))
    }


    override suspend fun run(execution: StepExecution): Any? {
        val invocation = count.incrementAndGet()
        execution.traceDetail(invocation)
        return invocation
    }
}
