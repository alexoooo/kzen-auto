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
import tech.kzen.auto.server.service.v1.*
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap


@Suppress("CanBeParameter")
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
    private val logicHandleFacade = LogicHandleFacade(runExecutionId, logicHandle)

    private var activeSequenceModel = ActiveSequenceModel()
    private var previousGraphInstance = GraphInstance(ObjectLocationMap.empty())


    //-----------------------------------------------------------------------------------------------------------------
    fun init(logicControl: LogicControl) {
        activeSequenceModel = ActiveSequenceModel()
        previousGraphInstance = GraphInstance(ObjectLocationMap.empty())
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
        val command = logicControl.pollCommand()
        logger.info("{} - run - {}", documentPath, command)

        if (command == LogicCommand.Cancel) {
            return LogicResultCancelled
        }

        val graphInstance = KzenAutoContext.global().graphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath))

        // TODO: handle rename refactoring
        activeSequenceModel.steps.keys.retainAll(graphInstance.keys)

        for (objectLocation in graphInstance.keys) {
            val previousInstance = previousGraphInstance[objectLocation]
                ?.reference as? StatefulLogicElement<*>
                ?: continue

            val currentInstance = graphInstance[objectLocation]!!
                .reference as? StatefulLogicElement<*>
                ?: continue

            if (previousInstance.javaClass != currentInstance.javaClass) {
                continue
            }

            loadStateUnchecked(currentInstance, previousInstance)
        }
        previousGraphInstance = graphInstance

        val stepContext = StepContext(
            logicControl, activeSequenceModel, logicHandleFacade, logicTraceHandle, graphInstance)

        val step = graphInstance[objectLocation]!!.reference as SequenceStep
        val model = activeSequenceModel.steps.getOrPut(objectLocation) { ActiveStepModel() }

        model.traceState = StepTrace.State.Active

        var stepValue: LogicResult
        try {
            stepValue = step.continueOrStart(stepContext)
            model.value = stepValue
        }
        catch (e: Throwable) {
            model.error = ExecutionFailure.ofException(e).errorMessage
            stepValue = LogicResultFailed(model.error!!)
        }

        if (stepValue is LogicResultSuccess) {
            model.traceState = StepTrace.State.Done
        }

        return stepValue
    }


    private fun loadStateUnchecked(a: StatefulLogicElement<*>, b: StatefulLogicElement<*>) {
        @Suppress("UNCHECKED_CAST")
        (a as StatefulLogicElement<Any>).loadState(b)
    }


    override fun close(error: Boolean) {
        logger.info("{} - close - {}", documentPath, error)
    }
}