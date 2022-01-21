package tech.kzen.auto.server.objects.sequence

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.ActiveStepModel
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.LogicHandle
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class SequenceExecution(
//    private val steps: List<SequenceStep<*>>,
    private val documentPath: DocumentPath,
    private val stepLocations: List<ObjectLocation>,
    private val logicHandle: LogicHandle,
    private val trace: LogicTraceHandle,
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
        logger.info("arguments - {}", arguments)
        return true
    }


    override fun continueOrStart(
        control: LogicControl,
        graphDefinition: GraphDefinition
    ): LogicResult {
        logger.info("run - {}", control.pollCommand())

        val graphInstance = ServerContext.graphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath))

        val activeSequenceModel = ActiveSequenceModel()

        for (stepLocation in stepLocations) {
            val step = graphInstance[stepLocation]!!.reference as SequenceStep<*>
            val model = activeSequenceModel.steps.getOrPut(stepLocation) { ActiveStepModel() }

            try {
                val stepValue = step.perform(activeSequenceModel, logicHandle)
                model.value = stepValue
            }
            catch (e: Exception) {
                model.error = ExecutionFailure.ofException(e).errorMessage
            }
//            stepValue.value
        }

        return LogicResultSuccess(TupleValue.ofMain(
            "foo"))
    }


    override fun close(error: Boolean) {
        logger.info("close - {}", error)
    }
}