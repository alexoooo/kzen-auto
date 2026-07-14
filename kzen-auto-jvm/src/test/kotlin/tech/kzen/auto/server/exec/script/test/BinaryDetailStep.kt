package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.BinaryExecutionValue


/**
 * A test-only [ScriptStep] that traces a small binary detail (a stand-in screenshot): a binary
 * [StepExecution.traceDetail] joins the run's retained history film-strip via
 * [tech.kzen.lib.common.exec.engine.Execution.log], so per-iteration trace-reset tests can assert the
 * film-strip survives while the live per-step values clear.
 */
class BinaryDetailStep: ScriptStep {
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        execution.traceDetail(BinaryExecutionValue(byteArrayOf(0x42)))
        return null
    }
}
