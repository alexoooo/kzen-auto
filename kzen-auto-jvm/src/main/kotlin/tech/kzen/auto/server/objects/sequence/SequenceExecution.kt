package tech.kzen.auto.server.objects.sequence

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.sequence.model.SequenceTree
import tech.kzen.auto.common.objects.document.sequence.model.StepTrace
import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunExecutionId
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.ActiveStepModel
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
import tech.kzen.auto.server.service.v1.*
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.util.ExceptionUtils


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
    private var arguments = TupleValue.empty
//    private var topLevel: Boolean = false


    //-----------------------------------------------------------------------------------------------------------------
    fun init(
        @Suppress("UNUSED_PARAMETER")
        logicControl: LogicControl
    ) {
        activeSequenceModel = ActiveSequenceModel()
        previousGraphInstance = GraphInstance(ObjectLocationMap.empty())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun beforeStart(arguments: TupleValue/*, topLevel: Boolean*/): Boolean {
        logger.info("{} - arguments - {}", documentPath, arguments)
        this.arguments = arguments
//        this.topLevel = topLevel
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

        val graphNotation = graphDefinition.graphStructure.graphNotation
        val validation = SequenceValidator.validate(documentPath, graphNotation, graphDefinition, graphInstance)
        val sequenceTree = SequenceTree.read(documentPath, graphDefinition)

        val stepContext = SequenceExecutionContext(
            logicControl,
            activeSequenceModel,
            logicHandleFacade,
            logicTraceHandle,
            graphInstance,
            arguments,
            sequenceTree,
            validation
            /*topLevel*/)

        val step = graphInstance[objectLocation]!!.reference as SequenceStep
        val stepModel = activeSequenceModel.steps.getOrPut(objectLocation) { ActiveStepModel() }

        stepModel.traceState = StepTrace.State.Active

        var logicResult: LogicResult
        try {
            logicResult = step.continueOrStart(stepContext)

            stepModel.value = (logicResult as? LogicResultSuccess)?.value

            if (logicResult is LogicResultFailed) {
                stepModel.error = logicResult.message
                logger.warn("Step execution failed: {}", logicResult.message)
            }
            else {
                stepModel.error = null
            }
        }
        catch (t: Throwable) {
            stepModel.value = null

            val message = ExceptionUtils.message(t)
            stepModel.error = message
            logicResult = LogicResultFailed(message)
            logger.warn("Step execution error", t)
        }

        if (logicResult.isTerminal()) {
            stepModel.traceState = StepTrace.State.Done
        }

        return logicResult
    }


    private fun loadStateUnchecked(a: StatefulLogicElement<*>, b: StatefulLogicElement<*>) {
        @Suppress("UNCHECKED_CAST")
        (a as StatefulLogicElement<Any>).loadState(b)
    }


    override fun close(error: Boolean) {
        logger.info("{} - close - {}", documentPath, error)
    }
}