package tech.kzen.auto.server.objects.script.api

import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.BinaryExecutionValue
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
        val stableId = stepContext.objectStableMapper.objectStableId(selfLocation)
        stepContext.logicTraceHandle.set(
            LogicTracePath.ofObjectStableId(stableId),
            activeModel.trace().asExecutionValue())

        // Record screenshots (binary details) on the retained history timeline so loop iterations and
        // nested executions all survive for the RunStep detail film strip. A screenshot is the only
        // binary detail today; the timeline itself stays value-agnostic (any Logic can append).
        if (detail is BinaryExecutionValue) {
            stepContext.logicTraceHandle.append(stableId, detail)
        }
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