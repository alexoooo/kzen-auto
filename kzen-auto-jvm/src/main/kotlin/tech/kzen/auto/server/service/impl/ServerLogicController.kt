package tech.kzen.auto.server.service.impl

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.paradigm.flow.service.format.FlowMessageInspector
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.job.service.JobWorkPool
import tech.kzen.auto.server.exec.job.EngineJobControl
import tech.kzen.auto.server.exec.report.ExecutionLogicTraceHandle
import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.NodeId
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.common.exec.engine.StepMode
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
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
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
 * Script, Flow, Job and Report documents all compile onto the engine now (a document of any other type fails to
 * compile → clean 400). The Job port runs its Workers as concurrent confined child nodes; live worker progress
 * is bridged back to the JS Job UI via [JobConventions.workerProgressPath] (see [mirrorTrace]). A Report runs its
 * record pipeline on the run's root node, bridging input / output progress to its literal trace paths.
 *
 * Run-lifecycle convergence: every control action that releases work (resume / step / cancel) is driven on a
 * single-thread executor that then blocks in [RunEngine.awaitQuiescent] until the run settles at its next
 * wavefront (a pause boundary or a terminal outcome), at which point [settleAfterDrive] reflects the settled
 * state back into the status flags. Signal-only actions (pause / cancel / setPauseOnError) call the engine
 * directly so they reach an in-flight run without queueing behind the busy executor.
 *
 * Live-edit migration (logic-spec §5): the client re-reads the (possibly edited) notation and passes it as the
 * run's `snapshotGraphDefinitionAttempt` each time it releases work. When that definition differs from the one
 * the live run was compiled against (a pause → edit → resume), [pendingMigration] recompiles the root [Logic]
 * and the executor calls [RunEngine.migrate] at the quiescent barrier instead of plain resume / step — so the
 * edit takes effect on the live run. This is flavour-agnostic: a Job carries its Worker state + channel
 * carryover across the rebuild (see [tech.kzen.auto.server.exec.job.JobRun]); a Script / Flow registers no
 * capture yet, so it cleanly restarts on the edited definition (the safe best-effort §5 default).
 */
class ServerLogicController(
    private val graphStore: LocalGraphStore,
    private val objectStableMapper: ObjectStableMapper,
    private val logicTraceStore: LogicTraceStore,
    private val cachedKotlinCompiler: CachedKotlinCompiler,
    private val flowMessageInspector: FlowMessageInspector,
    private val notationMetadataReader: NotationMetadataReader,
    private val jobWorkPool: JobWorkPool,
    private val environment: () -> GraphEnvironment
):
    LogicController
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ServerLogicController::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class LogicState(
        val runId: LogicRunId,
        val runExecutionId: LogicRunExecutionId,
        val engine: RunEngine,
        val rootLocation: ObjectLocation,

        // The transitive-closure object NOTATIONS (root document + everything it references) the live engine
        // tree was last compiled against — the change-detection baseline for a live edit. Keyed off notation,
        // NOT the compiled definition: a fresh definition build embeds freshly-constructed mutable runtime
        // scaffolding (e.g. a Flow vertex's MutableFlowOutput / MutableRequiredInput channel instances) with
        // identity equality, so two builds of the SAME notation are never definition-equal — which would make
        // every no-edit step / resume spuriously migrate (a step then re-parks at the same wavefront and never
        // advances). Notation is parsed-YAML data with structural equality, so it compares equal iff the user
        // actually edited. Updated on each migrate so the next compare is vs the currently-running notation.
        var baselineNotations: Map<ObjectLocation, ObjectNotation>
    ) {
        // Set once, immediately after construction (the observer references this state).
        lateinit var traceBridge: AutoCloseable

        // Per-engine-node trace buffer handles, created lazily on a node's first mirrored event (see
        // [handleForNode]) and cached for the run. Keying each node's trace by its own node id — the same id
        // the client addresses a live frame by — is what scopes a re-entered sub-logic to a fresh buffer
        // instead of ghosting the prior invocation's per-step values. Touched only from the trace bridge,
        // which is serialized on [bridgeLock].
        val nodeHandles = HashMap<NodeId, LogicTraceHandle>()

        // Nodes whose trace buffer has already been evicted on frame close (§7 streaming bounding): a node that
        // hosted with retainTrace = false, evicted once it settled terminal (see [evictClosedFrames]). Tracked so
        // the once-per-frame eviction is idempotent across the many publishes a run emits. Touched only from the
        // trace bridge, serialized on [bridgeLock].
        val evictedNodes = HashSet<NodeId>()

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

        // The run identity is generated before compiling: a flavour that persists run artifacts keyed to the
        // run (Report stamps its run dir) reads it off the compiler services. It stays fixed across a later
        // migrate recompile (same run).
        val runExecutionId = LogicRunExecutionId.random()

        val logic =
            try {
                compileLogic(root, graphDefinitionAttempt, runExecutionId)
            }
            catch (e: Throwable) {
                // Not a supported flavour, or the definition is incomplete. Fail gracefully (null → clean 400)
                // instead of letting it escape as a 500. A NotImplementedError (Error, not Exception) is the
                // unported-flavour signal, so catch Throwable.
                logger.warn("Unable to compile logic: {}", root, e)
                return null
            }

        val runId = runExecutionId.logicRunId

        val rootStableId = objectStableMapper.objectStableId(root)
        val engine = RunEngine(logic, rootStableId)
        engine.pauseOnError(pauseOnError)

        // A new run starts from a clean slate: drop every prior run's retained trace (values + the append-only
        // film-strip) so stale per-step displays don't bleed into this run. Child trace buffers are then created
        // lazily by the bridge as each node emits (see [handleForNode]); the ROOT's is created eagerly below.
        logicTraceStore.clearAll()

        val state = LogicState(
            runId, runExecutionId, engine, root,
            closureNotations(graphDefinitionAttempt, root.documentPath))
        state.traceBridge = engine.observe { mirrorTrace(state) }
        stateOrNull = state

        // Register the run's ROOT trace buffer up front, so the run is discoverable by its root location via
        // [LogicTraceStore.mostRecent] (the "run-scope entry point") the moment it starts — before any event is
        // emitted. A Script / Flow / Report root emits per-element trace events and so self-registers on its first
        // emit; a Job's root only HOSTS its Workers, each of which is its own node registering its OWN stable id,
        // so the Job root would otherwise NEVER enter the trace history — leaving the JS Job UI's
        // `fetchWorkerProgress` (mostRecent(main) -> lookupRun) with no run to resolve, hiding all live Preview /
        // worker progress. Idempotent with the later bridge emits (same node id -> same cached handle / buffer).
        handleForNode(state, engine.snapshot().root.id, rootStableId)

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


    // Atomic "start stepping": launch a fresh (never-launched) run parked at entry, then run exactly its first
    // step in [mode] — settling before the second. This is the single operation behind the "Start Stepping"
    // control (logicStartAndStep): "start a fresh run in stepping mode; this also executes the first step". It
    // MUST be atomic: composing it from a separate pause() + step() races, because pause()'s pause-at-entry
    // launches asynchronously on the executor (setting running) and the immediately-following step()'s guard
    // (!running) then trips with "Can't step, already running".
    //
    // [mode] is the mode of that FIRST step. Default [StepMode.Into] is the plain "Start Stepping"; [StepMode.Over]
    // is "Start Stepping Over" — it runs any sub-Logic the first boundary enters (e.g. a Job's RunWorker child, or
    // a Script whose first step is a RunStep) to completion rather than descending into it. This is what powers
    // slow-motion auto-step-over: without it the run would descend into the child on the bootstrap step and only
    // climb back out on the first subsequent Step Over.
    @Synchronized
    fun startStep(runId: LogicRunId, mode: StepMode = StepMode.Into): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        check(!state.launched) { "Can't start stepping, already running" }
        check(!state.cancelRequested) { "Can't start stepping, cancel already requested" }

        state.launched = true
        state.stepping = true

        executor.execute {
            // The first step launches the run parked at entry (before the first step; the launch is always
            // mode-agnostic — the never-started engine simply parks at the entry wavefront); the intermediate
            // quiesce lets it park so the second step has a parked node to drain — running that first step in
            // [mode] and parking before the next. Mirrors pause-at-entry followed by one Step [mode].
            state.engine.step(StepMode.Into)
            state.engine.awaitQuiescent()
            state.engine.step(mode)
            state.engine.awaitQuiescent()
            synchronized(this@ServerLogicController) {
                settleAfterDrive(state)
            }
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

        val migration = pendingMigration(state, snapshotGraphDefinitionAttempt)

        state.pauseRequested = false
        state.launched = true
        state.running = true

        executor.execute {
            state.engine.awaitQuiescent()
            if (migration != null) {
                // The notation changed under the paused run: rebuild from the edit and run free (the migrate
                // carries each flavour's surviving state across by stable id).
                state.engine.migrate(migration, paused = false)
            }
            else {
                state.engine.resume()
            }
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
        return drive(runId, StepMode.Into, snapshotGraphDefinitionAttempt)
    }


    @Synchronized
    override fun stepOver(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        return drive(runId, StepMode.Over, snapshotGraphDefinitionAttempt)
    }


    @Synchronized
    override fun stepOut(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        return drive(runId, StepMode.Out, snapshotGraphDefinitionAttempt)
    }


    private fun drive(
        runId: LogicRunId,
        mode: StepMode,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        check(!state.running && !state.stepping) { "Can't step, already running" }
        check(!state.cancelRequested) { "Can't step, cancel already requested" }

        val migration = pendingMigration(state, snapshotGraphDefinitionAttempt)

        state.pauseRequested = false
        state.launched = true
        state.stepping = true

        executor.execute {
            state.engine.awaitQuiescent()
            if (migration != null) {
                // Step after an edit: rebuild from the edit and park at the new definition's first wavefront —
                // a bounded step-after-edit (matching the old executor's re-park-fresh-then-report-paused).
                state.engine.migrate(migration, paused = true)
            }
            else {
                state.engine.step(mode)
            }
            state.engine.awaitQuiescent()
            synchronized(this@ServerLogicController) {
                settleAfterDrive(state)
            }
        }

        return LogicRunResponse.Submitted
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
    // Each event is routed to ITS EMITTING NODE's own trace buffer (keyed by the node id — see [handleForNode]),
    // not one shared run buffer. Every hosted child (a RunStep's sub-Script, a Job worker, a Flow child) is a
    // distinct node, so its trace is isolated per invocation: the client's frame-keyed lookup resolves exactly
    // that invocation, and a re-entered sub-logic starts from a fresh buffer instead of ghosting the prior
    // invocation's per-step displays (the trace store clears a prior same-stable-id buffer's live values when a
    // new execution re-opens it — the anti-ghost on re-entry).
    //
    // The engine attributes an emit to (node, address): the node carries the flavour's root stable id, while
    // the per-element stable id is the address — a Script emits `Address.of(stepStableId.value)` per step, a
    // Flow `Address.of(vertexStableId.value)` per vertex. The old trace store keys per element by stable id,
    // so the element id is reconstructed from the address segment (flavour-agnostic). Three flavour-specific
    // addresses are routed by reserved marker instead: the Script [ScriptRunContext.nextStepAddressMarker]
    // "next to run" highlight → the fixed next-step trace path (Flow never emits it); a Job Worker's
    // [EngineJobControl.workerProgressAddressMarker] live progress → that Worker's progress path (keyed by the
    // node's stable id, which IS the Worker's stable id) — the path the JS Job UI polls; and a Report's
    // [ExecutionLogicTraceHandle.tracePathAddressMarker] input / output progress → the literal trace path
    // carried in the remaining address segments (Report's trace paths are by-convention, not per-element).
    private fun mirrorTrace(state: LogicState) {
        synchronized(state.bridgeLock) {
            val events = state.engine.history(state.bridgedSequence)
            for (event in events) {
                val handle = handleForNode(state, event.nodeId, event.stableId)
                val address = event.address
                if (address != null && address.segments.isNotEmpty()) {
                    when (val segment = address.segments.first()) {
                        ScriptRunContext.nextStepAddressMarker ->
                            handle.set(ScriptConventions.nextStepTracePath, event.value)

                        EngineJobControl.workerProgressAddressMarker ->
                            handle.set(
                                JobConventions.workerProgressPath(event.stableId), event.value)

                        ExecutionLogicTraceHandle.tracePathAddressMarker ->
                            // A Report emits its input / output progress at a literal trace path: the remaining
                            // address segments ARE the LogicTracePath to set (no stable-id translation).
                            handle.set(
                                LogicTracePath(address.segments.drop(1)), event.value)

                        else ->
                            handle.set(
                                LogicTracePath.ofObjectStableId(ObjectStableId(segment)), event.value)
                    }
                }
                else {
                    handle.append(event.stableId, event.value)
                }
                if (event.sequence > state.bridgedSequence) {
                    state.bridgedSequence = event.sequence
                }
            }

            // §7 retention-vs-bounding: after mirroring this wavefront's events (so a just-closed frame's final
            // events are already recorded), reclaim the trace buffer of any frame that closed and opted OUT of
            // retention — bounding a streaming host to its live frames instead of leaking one buffer per element.
            evictClosedFrames(state)
        }
    }


    // Evict the trace buffer of every frame that has settled terminal while hosting with retainTrace = false
    // (see [tech.kzen.lib.common.exec.engine.Node.retainTrace] / [tech.kzen.lib.common.exec.engine.Execution.host]):
    // a long STREAMING host (one child per element) opts its per-element frames out of retention so their finished
    // buffers don't accumulate for the life of the run. A retained frame (the default — a Script RunStep's
    // sub-script, whose per-iteration screenshot strip needs every finished invocation) is never touched, so
    // post-run review is unaffected; the run root is retained by construction. Eviction is once-per-frame
    // ([LogicState.evictedNodes]) and idempotent across the run's many publishes. Called under [bridgeLock].
    private fun evictClosedFrames(state: LogicState) {
        forEachNode(state.engine.snapshot().root) { node ->
            if (node.status is NodeStatus.Terminal && !node.retainTrace && node.id !in state.evictedNodes) {
                state.evictedNodes.add(node.id)
                state.nodeHandles.remove(node.id)
                logicTraceStore.evict(LogicRunExecutionId(state.runId, LogicExecutionId(node.id.value)))
            }
        }
    }


    private fun forEachNode(node: Node, action: (Node) -> Unit) {
        action(node)
        for (child in node.children) {
            forEachNode(child, action)
        }
    }


    // The trace buffer handle for the engine node [nodeId], created lazily on its first mirrored event and
    // cached for the run. Every node — the root logic and each hosted child (a RunStep's sub-Script, a Job
    // worker, a Flow child) — gets its OWN buffer, keyed by its node id: the exact id the client addresses a
    // live frame by (run id + frame execution id). Frame-keyed lookups therefore resolve each invocation in
    // isolation, and — because the trace store clears a prior same-stable-id buffer's live values when a new
    // execution re-opens it — a re-entered sub-logic starts fresh instead of ghosting the prior invocation's
    // per-step displays. The buffer's object location comes from the event's own stable id; its parent
    // execution and hosting call-site — the execution-tree linkage [LogicTraceStore.lookupRunExecutions]
    // exposes, so a merged view can be scoped per hosting element (the RunStep screenshot strip) — are read
    // from the live snapshot tree.
    private fun handleForNode(
        state: LogicState,
        nodeId: NodeId,
        stableId: ObjectStableId
    ): LogicTraceHandle {
        return state.nodeHandles.getOrPut(nodeId) {
            val traceExecutionId = LogicRunExecutionId(state.runId, LogicExecutionId(nodeId.value))
            val located = locateNode(state.engine.snapshot().root, nodeId, null)
            val callerLocation = located?.node?.callerStableId?.let { objectStableMapper.objectLocation(it) }
            logicTraceStore.handle(
                traceExecutionId,
                objectStableMapper.objectLocation(stableId),
                located?.parentId?.let { LogicExecutionId(it.value) },
                callerLocation)
        }
    }


    private class NodeLocation(
        val node: Node,
        val parentId: NodeId?
    )


    // [target] and its parent id in the live node tree ([parentId] null for the root), or null when the node
    // is not currently in the tree — e.g. an event from a node the concurrent live-edit teardown already
    // removed. The tree is tiny (one node per live frame) and this is walked once per node, when its handle
    // is first created.
    private fun locateNode(node: Node, target: NodeId, parentId: NodeId?): NodeLocation? {
        if (node.id == target) {
            return NodeLocation(node, parentId)
        }
        for (child in node.children) {
            val nested = locateNode(child, target, node.id)
            if (nested != null) {
                return nested
            }
        }
        return null
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


    private fun compileLogic(
        root: ObjectLocation,
        attempt: GraphDefinitionAttempt,
        runExecutionId: LogicRunExecutionId
    ): Logic {
        return LogicCompiler.compile(
            root,
            attempt.graphStructure.graphNotation,
            attempt.transitiveSuccessful,
            LogicCompilerServices(
                environment(), objectStableMapper, cachedKotlinCompiler, flowMessageInspector,
                notationMetadataReader, jobWorkPool, runExecutionId))
    }


    // The recompiled root [Logic] to migrate the live run onto when its notation changed under a live edit, or
    // null to resume / step the existing tree. The change signal is the transitive-closure object NOTATIONS (see
    // [closureNotations] / [LogicState.baselineNotations]) — deterministic, so a no-edit release never migrates.
    // Only a LAUNCHED run migrates — an unlaunched engine has no live state to re-point, so the first release
    // just runs the start-time logic. A recompile failure (a mid-edit incomplete definition), or any failure
    // recomputing the closure, falls back to null — keeping the prior definition running rather than killing the
    // run.
    private fun pendingMigration(
        state: LogicState,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): Logic? {
        if (!state.launched) {
            return null
        }

        return try {
            val attempt = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)
            val editedNotations = closureNotations(attempt, state.rootLocation.documentPath)
            if (editedNotations == state.baselineNotations) {
                return null
            }

            val logic = compileLogic(state.rootLocation, attempt, state.runExecutionId)
            state.baselineNotations = editedNotations
            logic
        }
        catch (e: Throwable) {
            logger.warn("Unable to recompile edited logic, keeping prior definition: {}", state.rootLocation, e)
            null
        }
    }


    // The coalesced object notations of the root document's transitive closure (root document + every object it
    // references, across documents), keyed by location — the deterministic live-edit change signal. The closure
    // membership is taken from the successful definition (so a reference added / removed shifts the key set); the
    // per-object VALUE is the parsed-YAML notation, which — unlike the compiled definition — carries no
    // freshly-constructed mutable runtime scaffolding, so it compares equal across builds iff the user edited.
    // A closure member with no notation entry (a synthesized object, e.g. a Job's auto-wired channel, that is not
    // in the notation) is skipped: it is deterministically derived from notation objects that ARE in the closure,
    // so their comparison already carries any edit that would change it — skipping keeps the signal notation-only.
    private fun closureNotations(
        attempt: GraphDefinitionAttempt,
        documentPath: DocumentPath
    ): Map<ObjectLocation, ObjectNotation> {
        val closure = attempt.transitiveSuccessful.filterTransitive(documentPath)
        val graphNotation = attempt.graphStructure.graphNotation
        return closure.objectDefinitions.map.keys
            .mapNotNull { location ->
                graphNotation.coalesce[location]?.let { location to it }
            }
            .toMap()
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
