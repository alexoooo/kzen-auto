package tech.kzen.auto.server.objects.script

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.model.ActiveScriptModel
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.*
import tech.kzen.lib.common.exec.logic.model.*
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.ExceptionUtils


@Suppress("CanBeParameter")
class ScriptExecution(
    private val documentPath: DocumentPath,
    private val objectLocation: ObjectLocation,
    private val logicHandle: LogicHandle,
    private val logicTraceHandle: LogicTraceHandle,
    private val runExecutionId: LogicRunExecutionId,
    private val objectStableMapper: ObjectStableMapper,
    private val graphCreator: GraphCreator,
    private val environment: GraphEnvironment
):
    LogicExecution
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ScriptExecution::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val logicHandleFacade = LogicHandleFacade(runExecutionId, logicHandle)

    private val activeScriptModel = ActiveScriptModel()
    private var previousStatefulElements = mutableMapOf<ObjectStableId, StatefulLogicElement<*>>()
    private var arguments = TupleValue.empty


    //-----------------------------------------------------------------------------------------------------------------
    override fun beforeStart(arguments: TupleValue): Boolean {
        logger.info("{} - arguments - {}", documentPath, arguments)
        this.arguments = arguments
        return true
    }


    override fun continueOrStart(
        logicControl: LogicControl,
        resourceScope: LogicResourceScope,
        graphDefinition: GraphDefinition
    ): LogicResult {
        val command = logicControl.pollCommand()
        logger.info("{} - run - {}", documentPath, command)

        if (command == LogicCommand.Cancel) {
            return LogicResultCancelled
        }

        val graphInstance = graphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), environment)

        val liveStableIds = graphInstance.keys
            .map { objectStableMapper.objectStableId(it) }
            .toSet()
        activeScriptModel.steps.keys.retainAll(liveStableIds)

        val nextPreviousStatefulElements = mutableMapOf<ObjectStableId, StatefulLogicElement<*>>()
        for (currentLocation in graphInstance.keys) {
            val stableId = objectStableMapper.objectStableId(currentLocation)
            val currentInstance = graphInstance[currentLocation]!!
                .reference as? StatefulLogicElement<*>
                ?: continue

            nextPreviousStatefulElements[stableId] = currentInstance

            val previousInstance = previousStatefulElements[stableId]
                ?: continue

            if (previousInstance.javaClass != currentInstance.javaClass) {
                continue
            }

            loadStateUnchecked(currentInstance, previousInstance)
        }
        previousStatefulElements = nextPreviousStatefulElements

        val graphNotation = graphDefinition.graphStructure.graphNotation
        val validation = ScriptValidator.validate(
            documentPath, graphNotation, graphDefinition, graphInstance)
        val scriptTree = ScriptTree.read(documentPath, graphDefinition)

        val stepContext = ScriptExecutionContext(
            logicControl,
            resourceScope,
            activeScriptModel,
            logicHandleFacade,
            logicTraceHandle,
            graphInstance,
            graphDefinition,
            arguments,
            scriptTree,
            validation,
            objectStableMapper)

        val step = graphInstance[objectLocation]!!.reference as ScriptStep
        val stepModel = stepContext.getOrPutStepModel(objectLocation)

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