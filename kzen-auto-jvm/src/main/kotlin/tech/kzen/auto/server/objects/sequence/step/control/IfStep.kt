package tech.kzen.auto.server.objects.sequence.step.control

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.TextExecutionValue
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.common.paradigm.sequence.StepTrace
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveStepModel
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.objects.sequence.step.MultiSequenceStep
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentName
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentValue
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class IfStep(
    private val condition: ObjectLocation,
    then: List<ObjectLocation>,
    `else`: List<ObjectLocation>
):
    SequenceStep
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(IfStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private enum class State {
        Initial,
        ThenBranch,
        ElseBranch
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val thenDelegate = MultiSequenceStep(then)
    private val elseDelegate = MultiSequenceStep(`else`)

    private var state = State.Initial


    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.empty
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        if (state == State.Initial) {
            val conditionStep = stepContext.activeSequenceModel.steps[condition]
            val value = conditionStep?.value

            val mainValue: Any? =
                if (value is List<*>) {
                    val components = value.filterIsInstance<TupleComponentValue>()
                    components.find { it.name == TupleComponentName.main }?.value
                }
                else {
                    value
                }

            val conditionValue: Boolean = mainValue.toString() == "true"
            state =
                if (conditionValue) {
                    State.ThenBranch
                }
                else {
                    State.ElseBranch
                }
        }

//        val nextToRun =
        val step =
            if (state == State.ThenBranch) {
                thenDelegate
            }
            else {
                elseDelegate
            }

//        val stepModel = stepContext.activeSequenceModel.steps.getOrPut(nextToRun) { ActiveStepModel() }
//        val step = stepContext.graphInstance[nextToRun]!!.reference as SequenceStep

//        val logicTracePath = LogicTracePath.ofObjectLocation(nextToRun)
//        stepModel.traceState = StepTrace.State.Running
//        stepContext.logicTraceHandle.set(
//            logicTracePath,
//            stepModel.trace().asExecutionValue())

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val result =
            try {
                step.continueOrStart(stepContext)
            }
            catch (t: Throwable) {
                logger.warn("Branch error - {}", step, t)
                LogicResultFailed(ExecutionFailure.ofException(t).errorMessage)
            }

//        when (result) {
//            is LogicResultSuccess -> {
//                stepModel.value = result.value.components
//                stepModel.traceState = StepTrace.State.Done
//                stepContext.logicTraceHandle.set(
//                    logicTracePath,
//                    stepModel.trace().asExecutionValue())
//            }
//
//            is LogicResultFailed -> {
//                stepModel.error = result.message
//                stepModel.traceState = StepTrace.State.Done
//                stepContext.logicTraceHandle.set(
//                    logicTracePath,
//                    stepModel.trace().asExecutionValue())
//            }
//
//            LogicResultCancelled -> {
//                stepModel.traceState = StepTrace.State.Done
//                stepContext.logicTraceHandle.set(
//                    logicTracePath,
//                    stepModel.trace().asExecutionValue())
//            }
//
//            LogicResultPaused -> {
//                stepModel.traceState = StepTrace.State.Running
//                stepContext.logicTraceHandle.set(
//                    logicTracePath,
//                    stepModel.trace().asExecutionValue())
//            }
//        }

        return result
    }
}