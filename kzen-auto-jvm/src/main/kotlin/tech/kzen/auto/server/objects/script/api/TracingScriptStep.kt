package tech.kzen.auto.server.objects.script.api

import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation


abstract class TracingScriptStep(
    private val selfLocation: ObjectLocation
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    fun traceDetail(stepContext: ScriptExecutionContext, detail: Any?) {
        val detailValue =
            ExecutionValue.ofArbitrary(detail)
                ?: ExecutionValue.of(detail.toString())

        traceDetail(stepContext, detailValue)
    }


    fun traceDetail(stepContext: ScriptExecutionContext, detail: ExecutionValue) {
        val activeModel = stepContext.stepModel(selfLocation)!!
        activeModel.detail = detail
        stepContext.logicTraceHandle.set(
            stableIdTracePath(stepContext),
            activeModel.trace().asExecutionValue())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun traceValue(stepContext: ScriptExecutionContext, displayValue: Any?) {
        traceValue(stepContext, ExecutionValue.of(displayValue))
    }


    fun traceValue(stepContext: ScriptExecutionContext, displayValue: ExecutionValue) {
        val activeModel = stepContext.stepModel(selfLocation)!!
        activeModel.displayValue = displayValue
        stepContext.logicTraceHandle.set(
            stableIdTracePath(stepContext),
            activeModel.trace().asExecutionValue())
    }


    private fun stableIdTracePath(stepContext: ScriptExecutionContext): LogicTracePath {
        return LogicTracePath.ofObjectStableId(
            stepContext.objectStableMapper.objectStableId(selfLocation))
    }
}