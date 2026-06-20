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
import tech.kzen.lib.common.exec.logic.LogicResourceScope
import tech.kzen.lib.common.exec.logic.model.LogicCommand
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.server.exec.logic.context.LogicFrame
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
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
    private val logicTraceStore: LogicTraceStore,
    private val environment: () -> GraphEnvironment
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
        val frame: LogicFrame,
        val resourceScope: LogicResourceScope
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
        return start(root, snapshotGraphDefinitionAttempt, false)
    }


    @Synchronized
    fun start(
        root: ObjectLocation,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?,
        pauseOnError: Boolean
    ): LogicRunId? {
        val state = stateOrNull
        if (state != null) {
            return null
        }

        val runExecutionId = LogicRunExecutionId.random()
        val runId = runExecutionId.logicRunId
        val executionId = runExecutionId.logicExecutionId

        val graphDefinition = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)

        val successfulGraphDefinition = graphDefinition.successful()

        val transitiveDefinition = successfulGraphDefinition.filterTransitive(root)

        val rootGraphInstance =
            try {
                graphCreator.createGraph(transitiveDefinition, environment())
            }
            catch (e: Exception) {
                logger.info("Unable to create: {}", root, e)
                return null
            }

        val rootInstance = rootGraphInstance.objectInstances[root]?.reference
            ?: return null

        val commonMutableLogicControl = MutableLogicControl(pauseOnError)
        val resourceScope = MutableLogicResourceScope()

        val logicHandle: LogicHandle = object: LogicHandle {
            override fun start(
                logicRunExecutionId: LogicRunExecutionId,
                originalObjectLocation: ObjectLocation
            ): LogicExecutionFacade {
                val currentState = checkNotNull(stateOrNull)
                check(currentState.runId == runId)

                val hostFrame = currentState.frame.find(executionId)
                checkNotNull(hostFrame)

                val guestExecutionId = LogicExecutionId.random()

                val dependencies = CopyOnWriteArrayList<LogicFrame>()
                val listener = object: LogicExecutionListener {
                    override fun closed() {
                        hostFrame.dependencies.removeIf { it.executionId == guestExecutionId }
                    }
                }

                val logicExecutionFacadeImpl = LogicExecutionFacadeImpl(
                    successfulGraphDefinition, commonMutableLogicControl, resourceScope,
                    listener, logicTraceStore, environment)

                val logicExecution = logicExecutionFacadeImpl.open(
                    LogicRunExecutionId(runId, guestExecutionId), originalObjectLocation, this, graphCreator)

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
            resourceScope
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
            clearState(false)
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
        // Full-speed run: clear any leftover step budget / step-over mode (the command is None below,
        // so the budget path isn't consulted anyway — this just keeps the control state tidy).
        state.frame.control.grantStepBudget(0)
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
                        state.frame.control, state.resourceScope, graphDefinitionAttempt.successful())
                }
                catch (t: Throwable) {
                    logger.warn("Execution failed", t)
                    LogicResultFailed(ExceptionUtils.message(t))
                }

            synchronized(this@ServerLogicController) {
                state.running = false

                if (result.isTerminal()) {
                    state.frame.execution.close(result is LogicResultFailed)
                    clearState(result is LogicResultFailed)
                }
                else {
                    state.paused = true
                    // Converge on the same state as a user-initiated pause (e.g. after a
                    // pause-on-error) so a subsequent step() satisfies its pauseRequested /
                    // Pause-command preconditions. Idempotent for a real user pause: the flag is
                    // already set and commandPause() is then a no-op CAS.
                    state.pauseRequested = true
                    state.frame.control.commandPause()
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
        return stepInternal(runId, snapshotGraphDefinitionAttempt, stepOver = false)
    }


    @Synchronized
    override fun stepOver(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        return stepInternal(runId, snapshotGraphDefinitionAttempt, stepOver = true)
    }


    @Synchronized
    override fun stepOut(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        return stepInternal(runId, snapshotGraphDefinitionAttempt, stepOut = true)
    }


    private fun stepInternal(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?,
        stepOver: Boolean = false,
        stepOut: Boolean = false
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

        // Step Out: run the deepest currently-paused frame (and its descendants) to completion, pausing
        // back at the caller's next step — no fresh-step budget, but frames at depth >= the deepest run
        // free (see LogicControl.inStepOutRegion). Otherwise grant a single fresh-step budget for this
        // tick; for Step Over also flag the mode so a fresh RunStep descent runs its sub-document to
        // completion. One tick = exactly one fresh boundary advanced.
        if (stepOut) {
            state.frame.control.grantStepOut(deepestFrameDepth(state.frame))
        }
        else {
            state.frame.control.grantStepBudget(1, stepOver)
        }

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
                        state.frame.control, state.resourceScope, graphDefinitionAttempt.successful())
                }
                catch (t: Throwable) {
                    logger.warn("Execution failed", t)
                    LogicResultFailed(ExceptionUtils.message(t))
                }

            synchronized(this@ServerLogicController) {
                state.stepping = false

                if (result.isTerminal()) {
                    state.frame.execution.close(result is LogicResultFailed)
                    clearState(result is LogicResultFailed)
                }
                else {
                    // Re-affirm the paused-and-Pause-requested invariant (a step ending in a
                    // pause-on-error leaves the run paused and ready for another step/continue).
                    state.paused = true
                    state.pauseRequested = true
                    state.frame.control.commandPause()
                }
            }
        }

        return LogicRunResponse.Submitted
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Depth of the deepest frame on the (paused) spine: when paused the frame tree still holds the
    // cached child frames (RunStep keeps its pausedExecution), so this is the depth of the frame the
    // user is paused in — the one Step Out runs to completion.
    private fun deepestFrameDepth(frame: LogicFrame): Int {
        val dependencies = frame.dependencies
        if (dependencies.isEmpty()) {
            return 0
        }
        return 1 + dependencies.maxOf { deepestFrameDepth(it) }
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
    private fun clearState(error: Boolean) {
        val state = stateOrNull
            ?: return

        stateOrNull = null
        state.resourceScope.disposeAll(error)
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
                clearState(false)
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
                clearState(false)
            }
        }
    }
}