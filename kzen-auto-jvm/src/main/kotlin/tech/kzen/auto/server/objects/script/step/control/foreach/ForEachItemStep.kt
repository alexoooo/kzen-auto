package tech.kzen.auto.server.objects.script.step.control.foreach

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class ForEachItemStep(
    private val selfLocation: ObjectLocation
):
    TracingScriptStep(selfLocation)
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.any))
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        val parentLocation = selfLocation.parent()
            ?: return LogicResultFailed("Parent location not found")

        val parentForEach = scriptExecutionContext.graphInstance[parentLocation]!!.reference as? ForEachStep
            ?: return LogicResultFailed("Parent ForEach expected: $parentLocation")

        val next = parentForEach.next
            ?: return LogicResultFailed("Next item not found: $parentLocation")

        traceDetail(scriptExecutionContext, next)

        return LogicResultSuccess(
            TupleValue.ofMain(next))
    }
}