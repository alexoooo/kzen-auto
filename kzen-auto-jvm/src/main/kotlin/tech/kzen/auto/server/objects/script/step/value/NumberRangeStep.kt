package tech.kzen.auto.server.objects.script.step.value

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


@Reflect
class NumberRangeStep(
    private val from: Int,
    private val to: Int,
    private val selfLocation: ObjectLocation
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(NumberRangeStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(TupleDefinition.ofMain(
            LogicType(TypeMetadata(ClassNames.kotlinList, listOf(TypeMetadata.int), false))))
    }


    override fun continueOrStart(
        scriptExecutionContext: ScriptExecutionContext
    ): LogicResult {
        logger.info("{} - from = {} | to = {}", selfLocation, from, to)

        traceValue(scriptExecutionContext, "[$from .. $to]")

        val items = mutableListOf<Int>()
        for (n in from .. to) {
            items.add(n)
        }

        return LogicResultSuccess(
            TupleValue.ofMain(items))
    }
}