package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTracePath
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation


abstract class TracingSequenceStep(
    private val selfLocation: ObjectLocation
):
    SequenceStep
{
    //-----------------------------------------------------------------------------------------------------------------
    private val logicTracePath = LogicTracePath.ofObjectLocation(selfLocation)


    //-----------------------------------------------------------------------------------------------------------------
    fun traceDetail(stepContext: SequenceExecutionContext, detail: Any?) {
        val detailValue =
            ExecutionValue.ofArbitrary(detail)
                ?: ExecutionValue.of(detail.toString())

        traceDetail(stepContext, detailValue)
    }


    fun traceDetail(stepContext: SequenceExecutionContext, detail: ExecutionValue) {
        val activeModel = stepContext.activeSequenceModel.steps[selfLocation]!!
        activeModel.detail = detail
        stepContext.logicTraceHandle.set(
            logicTracePath,
            activeModel.trace().asExecutionValue())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun traceValue(stepContext: SequenceExecutionContext, displayValue: Any?) {
        traceValue(stepContext, ExecutionValue.of(displayValue))
    }


    fun traceValue(stepContext: SequenceExecutionContext, displayValue: ExecutionValue) {
        val activeModel = stepContext.activeSequenceModel.steps[selfLocation]!!
        activeModel.displayValue = displayValue
        stepContext.logicTraceHandle.set(
            logicTracePath,
            activeModel.trace().asExecutionValue())
    }
}