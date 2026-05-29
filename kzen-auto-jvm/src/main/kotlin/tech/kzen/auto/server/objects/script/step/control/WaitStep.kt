package tech.kzen.auto.server.objects.script.step.control

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class WaitStep(
    private val milliseconds: Long,
    private val selfLocation: ObjectLocation
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(WaitStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override fun continueOrStart(
        scriptExecutionContext: ScriptExecutionContext
    ): LogicResult {
        logger.info("{} - milliseconds = {}", selfLocation, milliseconds)

        traceDetail(scriptExecutionContext, "Waiting for $milliseconds milliseconds")

        Thread.sleep(milliseconds)

        traceDetail(scriptExecutionContext, "Finished waiting for $milliseconds milliseconds")

        return LogicResultSuccess(TupleValue.empty)
    }
}