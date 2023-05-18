package tech.kzen.auto.server.objects.sequence.step

import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.common.paradigm.sequence.StepTrace
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveStepModel
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class MultiSequenceStep(
    private val steps: List<ObjectLocation>
):
    SequenceStep//,
//    StatefulLogicElement<MultiSequenceStep>
{
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.empty
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        while (true) {
            val nextToRun = getAndPublishNextToRun(stepContext)
                ?: return LogicResultSuccess(TupleValue.empty)

            val logicCommand = stepContext.logicControl.pollCommand()
            if (logicCommand == LogicCommand.Cancel) {
                return LogicResultCancelled
            }
            else if (logicCommand == LogicCommand.Pause) {
                return LogicResultPaused
            }

            val model = stepContext.activeSequenceModel.steps.getOrPut(nextToRun) { ActiveStepModel() }
            val step = stepContext.graphInstance[nextToRun]!!.reference as SequenceStep

            val logicTracePath = LogicTracePath.ofObjectLocation(nextToRun)
            model.traceState = StepTrace.State.Running
            stepContext.logicTraceHandle.set(
                logicTracePath,
                model.trace().asExecutionValue())

            val result = step.continueOrStart(stepContext)
            if (result is LogicResultSuccess) {
                model.value = result.value.components
            }
            else {
                TODO("Not implemented (yet): $result")
            }

            model.traceState = StepTrace.State.Done
            stepContext.logicTraceHandle.set(
                logicTracePath,
                model.trace().asExecutionValue())
        }
    }


    private fun getAndPublishNextToRun(stepContext: StepContext): ObjectLocation? {
        val nextToRun = nextToRun(stepContext)

        if (nextToRun == null) {
            stepContext.activeSequenceModel.next = null
            stepContext.logicTraceHandle.set(
                SequenceConventions.nextStepTracePath,
                NullExecutionValue)
            return null
        }

        stepContext.activeSequenceModel.next = nextToRun
        stepContext.logicTraceHandle.set(
            SequenceConventions.nextStepTracePath,
            ExecutionValue.of(nextToRun.asString()))

        return nextToRun
    }


    private fun nextToRun(stepContext: StepContext): ObjectLocation? {
        for (stepLocation in steps) {
            val model = stepContext.activeSequenceModel.steps.getOrPut(stepLocation) { ActiveStepModel() }
            if (model.traceState == StepTrace.State.Done) {
                continue
            }
            return stepLocation
        }
        return null
    }


//    override fun loadState(previous: MultiSequenceStep) {
//        TODO("Not yet implemented")
//    }
}