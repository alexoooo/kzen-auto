package tech.kzen.auto.server.service.impl

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.paradigm.flow.service.format.FlowMessageInspector
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.NodeId
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.common.exec.engine.StepMode
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.run.LogicController
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.run.model.LogicStatus
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.server.exec.engine.RunEngine
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Clock


/**
 * Drives a single run on the new single-writer [RunEngine] (logic-spec greenfield core), while keeping the
 * existing [LogicController] REST contract intact so the client (status / start / run / step / pause / cancel)
 * is unchanged. The root document is translated to an engine [tech.kzen.lib.common.exec.engine.Logic] by
 * [LogicCompiler]; the engine's emitted trace events are bridged back into the existing [LogicTraceStore] so
 * the per-step value display keeps working.
 *
 * Script and Flow documents compile so far — Job translation is not yet ported, so starting a Job fails to
 * compile here (returns null → clean 400). This is the deliberate, temporary loss-of-parity step of the
 * Logic-framework migration; Job comes back once its flavour is ported onto the engine.
 *
 * Run-lifecycle convergence: every control action that releases work (resume / step / cancel) is driven on a
 * single-thread executor that then blocks in [RunEngine.awaitQuiescent] until the run settles at its next
 * wavefront (a pause boundary or a terminal outcome), at which point [settleAfterDrive] reflects the settled
 * state back into the status flags. Signal-only actions (pause / cancel / setPauseOnError) call the engine
 * directly so they reach an in-flight run without queueing behind the busy executor.
 */
class ServerLogicController(
    private val graphStore: LocalGraphStore,
    private val objectStableMapper: ObjectStableMapper,
    private val logicTraceStore: LogicTraceStore,
    private val cachedKotlinCompiler: CachedKotlinCompiler,
    private val flowMessageInspector: FlowMessageInspector,
    private val environment: () -> GraphEnvironment
):
    LogicController,
    NestedFrameRegistry
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ServerLogicController::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class LogicState(
        val runId: LogicRunId,
        val engine: RunEngine,
        val traceHandle: LogicTraceHandle
    ) {
        // Set once, immediately after construction (the observer references this state).
        lateinit var traceBridge: AutoCloseable

        // The engine has been launched (the root coroutine started). A fresh run is created but not launched;
        // the first drive (resume / step) — or a pause-at-entry — launches it.
        @Volatile
        var launched: Boolean = false

        // A full-speed run / a step is in flight on the driving executor (the run is executing, not settled).
        @Volatile
        var running: Boolean = false

        @Volatile
        var stepping: Boolean = false

        // User asked to pause / cancel a still-running run (drives the Pausing / Cancelling display until the
        // run settles).
        @Volatile
        var pauseRequested: Boolean = false

        @Volatile
        var cancelRequested: Boolean = false

        // Bridge cursor: the highest engine trace sequence already mirrored into the trace store. Guarded by
        // [bridgeLock] so concurrent publishes (engine dispatcher threads) mirror each event exactly once.
        val bridgeLock = Any()
        var bridgedSequence: Long = 0
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
        val time = Clock.System.now()

        val state = stateOrNull
            ?: return LogicStatus(time, null)

        val snapshot = state.engine.snapshot()

        val runState =
            if (state.cancelRequested) {
                LogicRunState.Cancelling
            }
            else if (state.stepping) {
                LogicRunState.Stepping
            }
            else if (state.running) {
                if (state.pauseRequested) LogicRunState.Pausing else LogicRunState.Running
            }
            else {
                when (deepestPauseReason(snapshot.root)) {
                    PauseReason.Error -> LogicRunState.ErrorPaused
                    PauseReason.Explicit -> LogicRunState.ExplicitPaused
                    else -> LogicRunState.Paused
                }
            }

        return LogicStatus(time, LogicRunInfo(state.runId, nodeToFrame(snapshot.root), runState))
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
        if (stateOrNull != null) {
            return null
        }

        val graphDefinitionAttempt = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)

        val logic =
            try {
                LogicCompiler.compile(
                    root,
                    graphDefinitionAttempt.graphStructure.graphNotation,
                    graphDefinitionAttempt.transitiveSuccessful,
                    LogicCompilerServices(
                        environment(), objectStableMapper, cachedKotlinCompiler, flowMessageInspector))
            }
            catch (e: Throwable) {
                // Not a supported flavour (Job not yet ported), or the definition is incomplete. Fail
                // gracefully (null → clean 400) instead of letting it escape as a 500. A NotImplementedError
                // (Error, not Exception) is the unported-flavour signal, so catch Throwable.
                logger.warn("Unable to compile logic: {}", root, e)
                return null
            }

        val runExecutionId = LogicRunExecutionId.random()
        val runId = runExecutionId.logicRunId

        val rootStableId = objectStableMapper.objectStableId(root)
        val engine = RunEngine(logic, rootStableId)
        engine.pauseOnError(pauseOnError)

        // A new run starts from a clean slate: drop every prior run's retained trace (values + the append-only
        // film-strip) so stale per-step displays don't bleed into this run. The run's root execution has no
        // caller / parent.
        logicTraceStore.clearAll()
        val traceHandle = logicTraceStore.handle(runExecutionId, root, null, null)

        val state = LogicState(runId, engine, traceHandle)
        state.traceBridge = engine.observe { mirrorTrace(state) }
        stateOrNull = state

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

        return state.engine.request(NodeId(executionId.value), request)
    }


    @Synchronized
    override fun cancel(runId: LogicRunId): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        state.cancelRequested = true

        // Signal directly (non-blocking) so the cancel reaches an in-flight run instead of queueing behind the
        // busy driving executor.
        state.engine.cancel()

        if (!state.running && !state.stepping) {
            // No in-flight drive task is waiting to observe the settle (the run was paused), so finalize the
            // now-cancelling run on the executor (which is free).
            executor.execute {
                state.engine.awaitQuiescent()
                synchronized(this@ServerLogicController) {
                    clearState(state)
                }
            }
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

        when {
            !state.launched -> {
                // Pause-at-entry: launch the run paused so it settles at the first step boundary (the first
                // step highlighted as "next to run"), matching the debugger's start-paused behaviour. A
                // subsequent step then runs that first step.
                state.launched = true
                state.running = true
                state.pauseRequested = true
                executor.execute {
                    state.engine.step(StepMode.Into)
                    state.engine.awaitQuiescent()
                    synchronized(this@ServerLogicController) {
                        settleAfterDrive(state)
                    }
                }
            }

            state.running -> {
                state.pauseRequested = true
                // Signal directly; the in-flight run settles at its next boundary and the driving task converges it.
                state.engine.pause()
            }

            // else: already settled at a pause — nothing to do.
        }

        return LogicRunResponse.Submitted
    }


    // Live-toggle pause-on-error on the active run (the header toggle, clickable while paused). Takes effect at
    // the next boundary the execution checks.
    @Synchronized
    fun setPauseOnError(runId: LogicRunId, value: Boolean): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        state.engine.pauseOnError(value)
        return LogicRunResponse.Submitted
    }


    @Synchronized
    override fun continueOrStart(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        check(!state.running && !state.stepping) { "Can't run, already running" }
        check(!state.cancelRequested) { "Can't run, cancel already requested" }

        state.pauseRequested = false
        state.launched = true
        state.running = true

        executor.execute {
            state.engine.resume()
            state.engine.awaitQuiescent()
            synchronized(this@ServerLogicController) {
                settleAfterDrive(state)
            }
        }

        return LogicRunResponse.Submitted
    }


    @Synchronized
    override fun step(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        return drive(runId, StepMode.Into)
    }


    @Synchronized
    override fun stepOver(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        return drive(runId, StepMode.Over)
    }


    @Synchronized
    override fun stepOut(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        return drive(runId, StepMode.Out)
    }


    private fun drive(runId: LogicRunId, mode: StepMode): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        check(!state.running && !state.stepping) { "Can't step, already running" }
        check(!state.cancelRequested) { "Can't step, cancel already requested" }

        state.pauseRequested = false
        state.launched = true
        state.stepping = true

        executor.execute {
            state.engine.step(mode)
            state.engine.awaitQuiescent()
            synchronized(this@ServerLogicController) {
                settleAfterDrive(state)
            }
        }

        return LogicRunResponse.Submitted
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Visibility-only attach of a detached nested Logic (a Job's Run Worker child). Not yet ported to the engine
    // (Job translation pending), so this is a no-op until then.
    @Synchronized
    override fun attach(
        hostExecutionId: LogicExecutionId,
        location: ObjectLocation,
        executionId: LogicExecutionId,
        execution: LogicExecution
    ): AutoCloseable {
        return AutoCloseable {}
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Must be invoked while synchronized on this controller, from the driving executor task once the engine has
    // quiesced. Either the run reached a terminal outcome (clear it) or it settled into a pause boundary.
    private fun settleAfterDrive(state: LogicState) {
        if (stateOrNull !== state) {
            return
        }

        state.running = false
        state.stepping = false
        state.pauseRequested = false

        val rootStatus = state.engine.snapshot().root.status
        if (rootStatus is NodeStatus.Terminal) {
            clearState(state)
        }
    }


    private fun clearState(state: LogicState) {
        if (stateOrNull !== state) {
            return
        }
        stateOrNull = null
        state.traceBridge.close()
        state.engine.close()
    }


    // Mirror the engine's newly-emitted trace events into the existing trace store so the client's per-step value
    // display keeps working unchanged. Invoked from the engine's observer (off the engine lock); serialized per
    // run on [LogicState.bridgeLock] so each event is written exactly once in sequence order.
    //
    // The engine attributes an emit to (node, address): the node carries the flavour's root stable id, while
    // the per-element stable id is the address — a Script emits `Address.of(stepStableId.value)` per step, a
    // Flow `Address.of(vertexStableId.value)` per vertex. The old trace store keys per element by stable id,
    // so the element id is reconstructed from the address segment (flavour-agnostic). The one Script-specific
    // address is the reserved [ScriptRunContext.nextStepAddressMarker] "next to run" highlight, routed to the
    // fixed next-step trace path; Flow never emits it (its client computes "next" itself from the vertices).
    private fun mirrorTrace(state: LogicState) {
        synchronized(state.bridgeLock) {
            val events = state.engine.history(state.bridgedSequence)
            for (event in events) {
                val address = event.address
                if (address != null && address.segments.isNotEmpty()) {
                    val segment = address.segments.first()
                    if (segment == ScriptRunContext.nextStepAddressMarker) {
                        state.traceHandle.set(ScriptConventions.nextStepTracePath, event.value)
                    }
                    else {
                        state.traceHandle.set(LogicTracePath.ofObjectStableId(ObjectStableId(segment)), event.value)
                    }
                }
                else {
                    state.traceHandle.append(event.stableId, event.value)
                }
                if (event.sequence > state.bridgedSequence) {
                    state.bridgedSequence = event.sequence
                }
            }
        }
    }


    private fun deepestPauseReason(node: Node): PauseReason? {
        val childReason = node.children.asReversed().firstNotNullOfOrNull { deepestPauseReason(it) }
        if (childReason != null) {
            return childReason
        }
        val status = node.status
        return if (status is NodeStatus.Suspended) status.reason else null
    }


    // The live frame tree (sidebar run indicator) shows only active frames: a completed child node lingers in
    // the engine's tree (for trace/history), but is pruned here so a hosted child that ran to completion
    // (step-over / step-out) no longer counts toward the paused stack depth — matching the old frame tree,
    // which removed a guest frame on close.
    private fun nodeToFrame(node: Node): LogicRunFrameInfo {
        return LogicRunFrameInfo(
            objectStableMapper.objectLocation(node.stableId),
            LogicExecutionId(node.id.value),
            node.children
                .filter { it.status !is NodeStatus.Terminal }
                .map { nodeToFrame(it) })
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        synchronized(this) {
            stateOrNull?.engine?.cancel()
        }

        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }

        synchronized(this) {
            val state = stateOrNull
            if (state != null) {
                stateOrNull = null
                state.traceBridge.close()
                state.engine.close()
            }
        }
    }
}
