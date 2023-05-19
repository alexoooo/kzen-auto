package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.lib.common.model.locate.ObjectLocation


abstract class TracingSequenceStep(
    private val selfLocation: ObjectLocation
):
    SequenceStep
{
    //-----------------------------------------------------------------------------------------------------------------
    private val logicTracePath = LogicTracePath.ofObjectLocation(selfLocation)


    //-----------------------------------------------------------------------------------------------------------------
    fun traceDetail(stepContext: StepContext, detail: Any) {
        traceDetail(stepContext, ExecutionValue.of(detail))
    }


    fun traceDetail(stepContext: StepContext, detail: ExecutionValue) {
        val activeModel = stepContext.activeSequenceModel.steps[selfLocation]!!
        activeModel.detail = detail
        stepContext.logicTraceHandle.set(
            logicTracePath,
            activeModel.trace().asExecutionValue())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun traceValue(stepContext: StepContext, displayValue: Any) {
        traceValue(stepContext, ExecutionValue.of(displayValue))
    }


    fun traceValue(stepContext: StepContext, displayValue: ExecutionValue) {
        val activeModel = stepContext.activeSequenceModel.steps[selfLocation]!!
        activeModel.displayValue = displayValue
        stepContext.logicTraceHandle.set(
            logicTracePath,
            activeModel.trace().asExecutionValue())
    }
}