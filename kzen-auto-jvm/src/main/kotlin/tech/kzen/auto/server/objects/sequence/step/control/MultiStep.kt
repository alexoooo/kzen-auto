package tech.kzen.auto.server.objects.sequence.step.control

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
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
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class MultiStep(
    private val steps: List<ObjectLocation>
):
    SequenceStep
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(MultiStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.empty
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        // TODO: handle step-into via RunStep while paused
//        var executeNextIfPaused = stepContext.topLevel
        var executeNextIfPaused = true

        var lastSuccessValue: TupleValue = TupleValue.empty

        while (true) {
            val nextToRun = getAndPublishNextToRun(stepContext)
                ?: return LogicResultSuccess(lastSuccessValue)

            val logicCommand = stepContext.logicControl.pollCommand()
            if (logicCommand == LogicCommand.Cancel) {
                return LogicResultCancelled
            }
            else if (! executeNextIfPaused && logicCommand == LogicCommand.Pause) {
                return LogicResultPaused
            }
            else {
                executeNextIfPaused = false
            }

            val stepModel = stepContext.activeSequenceModel.steps.getOrPut(nextToRun) { ActiveStepModel() }
            val step = stepContext.graphInstance[nextToRun]?.reference as? SequenceStep
                ?: throw IllegalStateException("Next step not found: $nextToRun")

            val logicTracePath = LogicTracePath.ofObjectLocation(nextToRun)
            stepModel.traceState = StepTrace.State.Running
            stepContext.logicTraceHandle.set(
                logicTracePath,
                stepModel.trace().asExecutionValue())

            @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
            val result =
                try {
                    step.continueOrStart(stepContext)
                }
                catch (t: Throwable) {
                    logger.warn("Step error - {}", nextToRun, t)
                    LogicResultFailed(ExecutionFailure.ofException(t).errorMessage)
                }

            when (result) {
                is LogicResultSuccess -> {
                    stepModel.value = result.value
                    stepModel.error = null
                    stepModel.traceState = StepTrace.State.Done
                    stepContext.logicTraceHandle.set(
                        logicTracePath,
                        stepModel.trace().asExecutionValue())
                    lastSuccessValue = result.value
                }

                is LogicResultFailed -> {
                    stepModel.value = null
                    stepModel.error = result.message
                    stepModel.traceState = StepTrace.State.Done
                    stepContext.logicTraceHandle.set(
                        logicTracePath,
                        stepModel.trace().asExecutionValue())
                    stepContext.logicTraceHandle.set(
                        SequenceConventions.nextStepTracePath,
                        NullExecutionValue)
                    return result
                }

                LogicResultCancelled -> {
                    stepModel.value = null
                    stepModel.error = null
                    stepModel.traceState = StepTrace.State.Done
                    stepContext.logicTraceHandle.set(
                        logicTracePath,
                        stepModel.trace().asExecutionValue())
                    return result
                }

                LogicResultPaused -> {
                    stepModel.value = null
                    stepModel.error = null
                    stepModel.traceState = StepTrace.State.Running
                    stepContext.logicTraceHandle.set(
                        logicTracePath,
                        stepModel.trace().asExecutionValue())
                    return result
                }
            }
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
}