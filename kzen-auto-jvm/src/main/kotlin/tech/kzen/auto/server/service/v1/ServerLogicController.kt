package tech.kzen.auto.server.service.v1

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.v1.LogicController
import tech.kzen.auto.common.paradigm.common.v1.model.*
import tech.kzen.auto.server.objects.logic.LogicTraceStore
import tech.kzen.auto.server.service.v1.model.LogicResultFailed
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.auto.server.service.v1.model.context.LogicFrame
import tech.kzen.auto.server.service.v1.model.context.MutableLogicControl
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


class ServerLogicController(
    private val graphStore: LocalGraphStore,
    private val graphCreator: GraphCreator
): LogicController {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ServerLogicController::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class LogicState(
        val runId: LogicRunId,
        val frame: LogicFrame,
        val objectStableMapper: ObjectStableMapper
    ) {
        var cancelRequested: Boolean = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var stateOrNull: LogicState? = null


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override fun status(): LogicStatus {
        val runInfo = stateOrNull?.let {
            val runState =
                if (it.cancelRequested) {
                    LogicRunState.Cancelling
                }
                else {
                    LogicRunState.Running
                }

            val frame = it.frame.toInfo(it.objectStableMapper)

            LogicRunInfo(
                it.runId,
                frame,
                runState
            )
        }

        val time = Clock.System.now()
        return LogicStatus(time, runInfo)
    }


    override suspend fun start(
        root: ObjectLocation,
        graphDefinitionSnapshot: GraphDefinitionAttempt?
    ): LogicRunId? {
        return startSynchronized(root, graphDefinitionSnapshot)
    }


    @Synchronized
    private fun startSynchronized(
        root: ObjectLocation,
        graphDefinitionSnapshot: GraphDefinitionAttempt?
    ): LogicRunId? {
        val state = stateOrNull
        if (state != null) {
            return null
        }

        val runId = LogicRunId(Clock.System.now().toString())
        val executionId = LogicExecutionId(runId.value)
        val runExecutionId = LogicRunExecutionId(runId, executionId)

        val objectStableMapper = ObjectStableMapper()

        val graphDefinition = graphDefinitionAttempt(graphDefinitionSnapshot)

        val successfulGraphDefinition = graphDefinition.successful()

        val transitiveDefinition = successfulGraphDefinition.filterTransitive(root)

        val rootGraphInstance =
            try {
                graphCreator.createGraph(transitiveDefinition)
            }
            catch (e: Exception) {
                logger.info("Unable to create: {}", root, e)
                return null
            }

        val rootInstance = rootGraphInstance.objectInstances[root]?.reference
            ?: return null

        val logicHandle: LogicHandle = object: LogicHandle {
            override fun start(originalObjectLocation: ObjectLocation): LogicHandle.Execution {
                TODO("Not yet implemented")
//                val dependencyGraphInstance = graphCreator.createGraph(
//                    successfulGraphDefinition.filterTransitive(originalObjectLocation))
//
//                val dependencyInstance = rootGraphInstance.objectInstances[root]?.reference as? Logic
//                    ?: throw IllegalArgumentException("Dependency logic not found: $originalObjectLocation")
//
//                val dependencyExecution = dependencyInstance.execute(this)
//
//                return object : LogicHandle.Execution {
//                    override fun next(arguments: TupleValue): LogicResult {
//                        return dependencyExecution.next(arguments)
//                    }
//
//                    override fun run(): LogicResult {
//                        dependencyExecution.run()
//                    }
//                }
            }
        }

        val logicTraceHandle = LogicTraceStore.handle(runExecutionId)

        val mutableLogicControl = MutableLogicControl()

        val logic = rootInstance as Logic
        val execution =
            try {
                logic.execute(logicHandle, logicTraceHandle, runExecutionId, mutableLogicControl)
            }
            catch (e: Exception) {
                logger.warn("Execution error: {}", root, e)
                return null
            }

        stateOrNull = LogicState(
            runId,
            LogicFrame(
                objectStableMapper.objectStableId(root),
                executionId,
                execution,
                LogicRunFrameState.Ready,
                listOf(),
                mutableLogicControl
            ),
            objectStableMapper
        )

        return runId
    }


    @Synchronized
    override fun request(
        runId: LogicRunId,
        executionId: LogicExecutionId,
        request: ExecutionRequest
    ): ExecutionResult {
        val state = stateOrNull
            ?: return ExecutionResult.failure(LogicConventions.notRunningError())

        if (state.runId != runId) {
            return ExecutionResult.failure(
                LogicConventions.wrongRunningError(runId, state.runId))
        }

        val frame = state.frame.find(executionId)
            ?: return ExecutionResult.failure(
                LogicConventions.missingExecution(executionId, runId))

        return frame.control.publishRequest(request)
    }


    @Synchronized
    override fun cancel(runId: LogicRunId): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.Rejected
        }

        state.frame.control.commandCancel()

        return LogicRunResponse.Submitted
    }


    @Synchronized
    override fun continueOrStart(
        runId: LogicRunId,
        graphDefinitionSnapshot: GraphDefinitionAttempt?
    ):
            LogicRunResponse
    {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.Rejected
        }

        val ready = state.frame.execution.beforeStart(TupleValue.empty)
        if (! ready) {
            return LogicRunResponse.Aborted
        }

        val graphDefinitionAttempt = graphDefinitionAttempt(graphDefinitionSnapshot)

        Thread {
            val result =
                try {
                    state.frame.execution.continueOrStart(
                        state.frame.control, graphDefinitionAttempt.successful())
                }
                catch (t: Throwable) {
                    LogicResultFailed(ExecutionFailure.ofException(t).errorMessage)
                }

            if (result.isTerminal()) {
                state.frame.execution.close(result is LogicResultFailed)
                clearState()
            }
        }.start()

        return LogicRunResponse.Submitted
    }


    private fun graphDefinitionAttempt(
        snapshot: GraphDefinitionAttempt?
    ): GraphDefinitionAttempt {
        if (snapshot != null) {
            return snapshot
        }

        return runBlocking {
            graphStore.graphDefinition()
        }
    }


    @Synchronized
    private fun clearState() {
        val state = stateOrNull
            ?: return

        state.frame.control.close()

        stateOrNull = null

        // NB: hit to GCs to give memory back to the OS
        System.gc()
    }
}