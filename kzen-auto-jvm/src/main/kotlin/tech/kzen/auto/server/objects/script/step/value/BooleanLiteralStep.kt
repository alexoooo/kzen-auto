package tech.kzen.auto.server.objects.script.step.value

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BooleanLiteralStep(
    private val value: Boolean,
    private val selfLocation: ObjectLocation
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(BooleanLiteralStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.boolean))
    }


    override fun continueOrStart(
        scriptExecutionContext: ScriptExecutionContext
    ): LogicResult {
        logger.info("{} - value = {}", selfLocation, value)

        traceValue(scriptExecutionContext, value)

        return LogicResultSuccess(
            TupleValue.ofMain(value))
    }
}