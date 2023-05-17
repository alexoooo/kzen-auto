package tech.kzen.auto.server.objects.sequence

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.sequence.StepTrace
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.ActiveStepModel
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.LogicHandle
import tech.kzen.auto.server.service.v1.LogicHandleFacade
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class SequenceExecution(
    private val documentPath: DocumentPath,
    private val objectLocation: ObjectLocation,
    private val logicHandle: LogicHandle,
    private val logicTraceHandle: LogicTraceHandle,
    private val runExecutionId: LogicRunExecutionId
):
    LogicExecution
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(SequenceExecution::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun init(logicControl: LogicControl) {

    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun beforeStart(arguments: TupleValue): Boolean {
        logger.info("{} - arguments - {}", documentPath, arguments)
        return true
    }


    override fun continueOrStart(
        logicControl: LogicControl,
        graphDefinition: GraphDefinition
    ): LogicResult {
        logger.info("{} - run - {}", documentPath, logicControl.pollCommand())

        val graphInstance = KzenAutoContext.global().graphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath))

        val activeSequenceModel = ActiveSequenceModel()
        val logicHandleFacade = LogicHandleFacade(runExecutionId, logicHandle)
        val stepContext = StepContext(
            logicControl, activeSequenceModel, logicHandleFacade, logicTraceHandle, graphInstance)

        val step = graphInstance[objectLocation]!!.reference as SequenceStep
        val model = activeSequenceModel.steps.getOrPut(objectLocation) { ActiveStepModel() }

        model.traceState = StepTrace.State.Active
        try {
            val stepValue = step.continueOrStart(stepContext)
            model.value = stepValue
        }
        catch (e: Throwable) {
            model.error = ExecutionFailure.ofException(e).errorMessage
        }
        model.traceState = StepTrace.State.Done

        return LogicResultSuccess(TupleValue.empty)
    }


    override fun close(error: Boolean) {
        logger.info("{} - close - {}", documentPath, error)
    }
}