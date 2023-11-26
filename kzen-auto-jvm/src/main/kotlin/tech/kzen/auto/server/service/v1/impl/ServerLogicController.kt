package tech.kzen.auto.server.service.v1.impl

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.v1.LogicController
import tech.kzen.auto.common.paradigm.common.v1.model.*
import tech.kzen.auto.server.objects.logic.LogicTraceStore
import tech.kzen.auto.server.service.v1.Logic
import tech.kzen.auto.server.service.v1.LogicExecutionFacade
import tech.kzen.auto.server.service.v1.LogicExecutionListener
import tech.kzen.auto.server.service.v1.LogicHandle
import tech.kzen.auto.server.service.v1.model.LogicCommand
import tech.kzen.auto.server.service.v1.model.LogicResultFailed
import tech.kzen.auto.server.service.v1.model.context.LogicFrame
import tech.kzen.auto.server.service.v1.model.context.MutableLogicControl
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import java.util.concurrent.CopyOnWriteArrayList


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
        var pauseRequested: Boolean = false

        @Volatile
        var paused: Boolean = false

        @Volatile
        var stepping: Boolean = false

        @Volatile
        var running: Boolean = false
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
                else if (it.stepping) {
                    LogicRunState.Stepping
                }
                else if (it.paused) {
                    LogicRunState.Paused
                }
                else if (it.pauseRequested) {
                    LogicRunState.Pausing
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


    @Synchronized
    override fun start(
        root: ObjectLocation,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunId? {
        val state = stateOrNull
        if (state != null) {
            return null
        }

        val runId = LogicRunId(LogicExecutionFacadeImpl.arbitraryId())
        val executionId = LogicExecutionId(runId.value)
        val runExecutionId = LogicRunExecutionId(runId, executionId)

        val objectStableMapper = ObjectStableMapper()

        val graphDefinition = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)

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

        val commonMutableLogicControl = MutableLogicControl()

        val logicHandle: LogicHandle = object: LogicHandle {
            override fun start(
                logicRunExecutionId: LogicRunExecutionId,
                originalObjectLocation: ObjectLocation
            ): LogicExecutionFacade {
                val currentState = checkNotNull(stateOrNull)
                check(currentState.runId == runId)

                val hostFrame = currentState.frame.find(executionId)
                checkNotNull(hostFrame)

                val guestExecutionId = LogicExecutionId(LogicExecutionFacadeImpl.arbitraryId())

                val dependencies = CopyOnWriteArrayList<LogicFrame>()
                val listener = object: LogicExecutionListener {
                    override fun closed() {
                        hostFrame.dependencies.removeIf { it.executionId == guestExecutionId }
                    }
                }

                val logicExecutionFacadeImpl = LogicExecutionFacadeImpl(
                    successfulGraphDefinition, commonMutableLogicControl, listener)

                val logicExecution = logicExecutionFacadeImpl.open(
                    runId, originalObjectLocation, this, graphCreator)

                val stableObjectLocation = objectStableMapper.objectStableId(originalObjectLocation)
                hostFrame.dependencies.add(LogicFrame(
                    stableObjectLocation,
                    guestExecutionId,
                    logicExecution,
                    dependencies,
                    commonMutableLogicControl
                ))

                return logicExecutionFacadeImpl
            }
        }

        val logicTraceHandle = LogicTraceStore.handle(runExecutionId, root)

        val logic = rootInstance as Logic
        val execution =
            try {
                logic.execute(logicHandle, logicTraceHandle, runExecutionId, commonMutableLogicControl)
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
                CopyOnWriteArrayList(),
                commonMutableLogicControl
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
            return LogicRunResponse.RunIdMismatch
        }

        state.cancelRequested = true

        if (state.paused) {
            state.frame.execution.close(false)
            clearState()
        }
        else {
            state.frame.control.commandCancel()
        }

        return LogicRunResponse.Submitted
    }


    @Synchronized
    override fun pause(runId: LogicRunId): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        check(! state.paused) { "Already paused" }

        state.pauseRequested = true
        state.frame.control.commandPause()

        if (! state.running) {
            state.paused = true
        }

        return LogicRunResponse.Submitted
    }


    @Synchronized
    override fun continueOrStart(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ):
        LogicRunResponse
    {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        check(! state.running) { "Can't run, already running" }
        check(! state.cancelRequested) { "Can't run, stop already requested" }
        state.pauseRequested = false
        state.paused = false
        state.frame.control.commandUnpause()

//        val topLevel = state.frame.dependencies.isEmpty()
        val ready = state.frame.execution.beforeStart(TupleValue.empty/*, topLevel*/)
        if (! ready) {
            return LogicRunResponse.UnableToStart
        }

        val graphDefinitionAttempt = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)

        state.running = true

        Thread {
            val result =
                try {
                    state.frame.execution.continueOrStart(
                        state.frame.control, graphDefinitionAttempt.successful())
                }
                catch (t: Throwable) {
                    logger.warn("Execution failed", t)
                    LogicResultFailed(ExecutionFailure.ofException(t).errorMessage)
                }

            state.running = false

            if (result.isTerminal()) {
                state.frame.execution.close(result is LogicResultFailed)
                clearState()
            }
            else {
                state.paused = true
            }
        }.start()

        return LogicRunResponse.Submitted
    }


    override fun step(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        // NB: "stepping" is just the same as running, but with the pause pre-selected
        check(! state.stepping) { "Can't step, already stepping" }
        check(! state.cancelRequested) { "Can't step, stop already requested" }
        check(state.pauseRequested) { "Must be paused in order to step" }
        check(state.paused) { "Must be paused in order to step" }
        state.stepping = true

        val command = state.frame.control.pollCommand()
        check(command == LogicCommand.Pause) { "Must be paused in order to step" }

//        val topLevel = state.frame.dependencies.isEmpty()
        val ready = state.frame.execution.beforeStart(TupleValue.empty/*, topLevel*/)
        if (! ready) {
            return LogicRunResponse.UnableToStart
        }

        val graphDefinitionAttempt = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)

        Thread {
            val result =
                try {
                    state.frame.execution.continueOrStart(
                        state.frame.control, graphDefinitionAttempt.successful())
                }
                catch (t: Throwable) {
                    logger.warn("Execution failed", t)
                    LogicResultFailed(ExecutionFailure.ofException(t).errorMessage)
                }

            state.stepping = false

            if (result.isTerminal()) {
                state.frame.execution.close(result is LogicResultFailed)
                clearState()
            }
        }.start()

        return LogicRunResponse.Submitted
    }


    //-----------------------------------------------------------------------------------------------------------------
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