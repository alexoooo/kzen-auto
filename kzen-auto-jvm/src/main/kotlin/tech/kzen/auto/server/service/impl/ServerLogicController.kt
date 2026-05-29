package tech.kzen.auto.server.service.impl

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.logic.run.LogicController
import tech.kzen.lib.common.exec.logic.run.model.*
import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicExecutionListener
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicCommand
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.server.exec.logic.context.LogicFrame
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.ExceptionUtils
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Clock


class ServerLogicController(
    private val graphStore: LocalGraphStore,
    private val graphCreator: GraphCreator,
    private val objectStableMapper: ObjectStableMapper,
    private val logicTraceStore: LogicTraceStore
):
    LogicController
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ServerLogicController::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class LogicState(
        val runId: LogicRunId,
        val frame: LogicFrame
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

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ServerLogicController-execution").apply {
            isDaemon = true
        }
    }


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

            val frame = it.frame.toInfo(objectStableMapper)

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
                originalObjectLocation: ObjectLocation,
                objectStableMapper: ObjectStableMapper
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
                    successfulGraphDefinition, commonMutableLogicControl, listener, logicTraceStore)

                val logicExecution = logicExecutionFacadeImpl.open(
                    runId, originalObjectLocation, this, graphCreator, objectStableMapper)

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

        val logicTraceHandle = logicTraceStore.handle(runExecutionId, root)

        val logic = rootInstance as Logic
        val execution =
            try {
                logic.execute(logicHandle, logicTraceHandle, runExecutionId, commonMutableLogicControl, objectStableMapper)
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
            )
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

        check(!state.paused) { "Already paused" }

        state.pauseRequested = true
        state.frame.control.commandPause()

        if (!state.running) {
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

        check(!state.running) { "Can't run, already running" }
        check(!state.cancelRequested) { "Can't run, stop already requested" }
        state.pauseRequested = false
        state.paused = false
        state.frame.control.commandUnpause()

//        val topLevel = state.frame.dependencies.isEmpty()
        val ready = state.frame.execution.beforeStart(TupleValue.empty/*, topLevel*/)
        if (!ready) {
            return LogicRunResponse.UnableToStart
        }

        val graphDefinitionAttempt = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)

        state.running = true

        executor.execute {
            val result =
                try {
                    state.frame.execution.continueOrStart(
                        state.frame.control, graphDefinitionAttempt.successful())
                }
                catch (t: Throwable) {
                    logger.warn("Execution failed", t)
                    LogicResultFailed(ExceptionUtils.message(t))
                }

            synchronized(this@ServerLogicController) {
                state.running = false

                if (result.isTerminal()) {
                    state.frame.execution.close(result is LogicResultFailed)
                    clearState()
                }
                else {
                    state.paused = true
                }
            }
        }

        return LogicRunResponse.Submitted
    }


    @Synchronized
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
        check(!state.stepping) { "Can't step, already stepping" }
        check(!state.cancelRequested) { "Can't step, stop already requested" }
        check(state.pauseRequested) { "Must be paused in order to step" }
        check(state.paused) { "Must be paused in order to step" }
        state.stepping = true

        val command = state.frame.control.pollCommand()
        check(command == LogicCommand.Pause) { "Must be paused in order to step" }

//        val topLevel = state.frame.dependencies.isEmpty()
        val ready = state.frame.execution.beforeStart(TupleValue.empty/*, topLevel*/)
        if (!ready) {
            return LogicRunResponse.UnableToStart
        }

        val graphDefinitionAttempt = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)

        executor.execute {
            val result =
                try {
                    state.frame.execution.continueOrStart(
                        state.frame.control, graphDefinitionAttempt.successful())
                }
                catch (t: Throwable) {
                    logger.warn("Execution failed", t)
                    LogicResultFailed(ExceptionUtils.message(t))
                }

            synchronized(this@ServerLogicController) {
                state.stepping = false

                if (result.isTerminal()) {
                    state.frame.execution.close(result is LogicResultFailed)
                    clearState()
                }
            }
        }

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

        stateOrNull = null
        state.frame.control.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        // Best-effort cooperative cancel of any in-flight run so the executor task
        // can run its own clearState() before we shut the executor down.
        synchronized(this) {
            val state = stateOrNull
            if (state != null && !state.running && !state.stepping) {
                state.frame.execution.close(false)
                clearState()
            }
            else {
                state?.cancelRequested = true
                state?.frame?.control?.commandCancel()
            }
        }

        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }

        // Safety net: if the executor task didn't get to clearState() (e.g.
        // shutdownNow interrupted it mid-execution), force the frame control close
        // so no LogicControl publisher hangs past controller shutdown.
        synchronized(this) {
            if (stateOrNull != null) {
                clearState()
            }
        }
    }
}