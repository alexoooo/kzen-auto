package tech.kzen.auto.server.service.impl

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.common.paradigm.logic.LogicControlReply
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.RunTraceAccess
import tech.kzen.auto.server.objects.job.JobValidationCache
import tech.kzen.auto.server.objects.job.service.JobWorkPool
import tech.kzen.auto.server.objects.script.ScriptValidationCache
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
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.server.exec.engine.RunEngine
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


/**
 * Drives a single run on the single-writer [RunEngine], keeping the [LogicController] REST contract
 * unchanged; the root document — any of the four Logic flavours, each with no per-flavour code here — is
 * translated to an engine [Logic] by [LogicCompiler]. The engine IS the trace store: the REST trace surface
 * projects the run's engine at query time ([tech.kzen.auto.server.exec.RunEngineLogicTrace], reachable via
 * [retainedTraceAccess]), and a settled run's engine is RETAINED for post-run review until the next [start]
 * (or [clearRetainedTrace]) disposes it — see kzen-auto architecture.md §3 for the wire-level picture.
 *
 * Run-lifecycle convergence: every control action that releases work (resume / step / cancel) is driven on a
 * single-thread executor that then blocks in [RunEngine.awaitQuiescent] until the run settles at its next
 * wavefront (a pause boundary or a terminal outcome), at which point [settleAfterDrive] reflects the settled
 * state back into the status flags. Signal-only actions (pause / cancel / setPauseOnError) call the engine
 * directly so they reach an in-flight run without queueing behind the busy executor.
 *
 * Live-edit migration (logic-spec §5): when the notation changed under a paused run, [pendingMigration]
 * recompiles the root [Logic] and the executor calls [RunEngine.migrate] at the quiescent barrier instead of
 * plain resume / step. Detection is two-stage (see [editDirty] and [pendingMigration]); the compared closure
 * spans the root document PLUS its linked logic documents ([LinkedLogicDocuments]), so editing a paused
 * caller's callee migrates the caller even though the `instructions` link is weak. Each flavour carries its
 * own surviving state across the rebuild by stable id (e.g. [tech.kzen.auto.server.exec.job.JobRun],
 * [tech.kzen.auto.server.exec.script.ScriptMigrationState]); the engine itself carries open resource
 * registrations by owning frame.
 */
class ServerLogicController(
    private val graphStore: LocalGraphStore,
    private val objectStableMapper: ObjectStableMapper,
    private val cachedKotlinCompiler: CachedKotlinCompiler,
    private val scriptValidationCache: ScriptValidationCache,
    private val jobValidationCache: JobValidationCache,
    private val notationMetadataReader: NotationMetadataReader,
    private val jobWorkPool: JobWorkPool,
    private val environment: GraphEnvironment
):
    LogicController,
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ServerLogicController::class.java)

        // How long close waits for the driving executor to finish cancelling before interrupting it
        private const val closeJoinTimeoutSeconds = 10L
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class LogicState(
        val runId: LogicRunId,
        val runExecutionId: LogicRunExecutionId,
        val engine: RunEngine,
        val rootLocation: ObjectLocation,

        // Content digest of the transitive-closure NOTATION (root document + everything it references, PLUS
        // each linked logic document's closure — weakly-referenced callees a plain root closure cannot see;
        // LinkedLogicDocuments.transitiveDigest) the live engine tree was last compiled against — the
        // change-detection baseline for a live edit. Keyed off notation, NOT the compiled definition: a fresh definition
        // build embeds freshly-constructed mutable runtime scaffolding (e.g. a Flow vertex's MutableFlowOutput /
        // MutableRequiredInput channel instances) with identity equality, so two builds of the SAME notation are
        // never definition-equal — which would make every no-edit step / resume spuriously migrate (a step then
        // re-parks at the same wavefront and never advances). Notation digests are content-derived, so they
        // compare equal iff the user actually edited. Updated on each migrate so the next compare is vs the
        // currently-running notation.
        var baselineClosureDigest: Digest
    ) {
        // The run reached a terminal outcome and its engine was shut down (pools stopped) but retained —
        // its node tree + history stay readable for post-run trace queries until the next start() (or a
        // global clear) disposes it. status() reports a settled state as no-active-run; the control methods
        // treat it as not-found. An O(1) flag set once at terminal settle, so status() needn't snapshot.
        @Volatile
        var settled: Boolean = false

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

        // This run's single subscription to its engine's change signal, re-broadcast to the controller's
        // statusObservers. Closed when the state is disposed: RunEngine.shutdown()/dispose() do NOT clear the
        // engine's observer list, so an unclosed subscription would outlive its run.
        var engineSubscription: AutoCloseable? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var stateOrNull: LogicState? = null

    // Surfaced as LogicStatus.epoch: the transitions a run's own trace sequence cannot express (a run
    // started, settled terminal, or a retained trace was cleared). Bumped under the controller lock, and
    // deliberately bumps even with no active run — what lets a client notice a post-run "clear traces".
    // Canonical semantics: architecture.md §3.
    private var epoch: Long = 0

    // Surfaced as LogicStatus.structureVersion: moves only on a genuine EXECUTION-TREE change, never on a
    // plain trace emit — what lets a structure-keyed consumer re-fetch on structure rather than per publish
    // (semantics: architecture.md §3). Computed LAZILY in status() (under this controller's monitor, off the
    // engine hot path) by diffing a cheap signature against the last; deliberately no reactive bump sites of
    // its own — every structural mutation already fires notifyStatusObservers(), and conflation of the push
    // channel can only COLLAPSE bumps, which is safe because the structure-keyed queries are full snapshots.
    private var structureVersion: Long = 0
    private var lastStructureSignature: StructureSignature? = null

    // The value status() diffs to decide whether structureVersion moved. nodeIds is the UNFILTERED set of
    // execution-node ids under snapshot.root — mirrors RunEngineLogicTrace's execution walk, NOT the
    // terminal-pruned nodeToFrame (architecture.md §3 explains why a frame-derived set would go stale).
    // Node ids are monotone (n0, n1, ...) and never revisited, so equal signatures ⇒ identical trees.
    private data class StructureSignature(
        val epoch: Long,
        val runId: LogicRunId?,
        val runState: LogicRunState?,
        val nodeIds: List<String>?)

    // Consumers of "the run status may have changed" — the push transport (/logic/events) is the only one.
    // Payload-free, mirroring the engine's own Run.observe contract: a listener is told THAT something changed
    // and pulls status() itself. Controller-scoped rather than one engine subscription per consumer — the
    // engine is replaced on each start() and never clears its observer list (architecture.md §3, "Subscribe to
    // the controller, never to an engine"); the controller holds exactly one subscription per run
    // (LogicState.engineSubscription) and fans out from here, and it also owns the epoch transitions no
    // engine can see.
    //
    // CONTRACT (load-bearing): a listener is invoked on an engine dispatcher thread, on the emit/log/park hot
    // path, and sometimes while this controller's monitor is held. It must do nothing but hand off to its own
    // scope (e.g. trySend into a CONFLATED channel) — never call status(), never serialize, never block.
    private val statusObservers = CopyOnWriteArraySet<() -> Unit>()

    fun observeStatus(listener: () -> Unit): AutoCloseable {
        statusObservers.add(listener)
        return AutoCloseable { statusObservers.remove(listener) }
    }

    private fun notifyStatusObservers() {
        for (observer in statusObservers) {
            try {
                observer()
            }
            catch (e: Throwable) {
                // An observer must never break the engine's hot path.
                logger.warn("Status observer error", e)
            }
        }
    }

    private fun bumpEpoch() {
        epoch += 1
        notifyStatusObservers()
    }

    // Every accepted control verb announces itself: a verb typically flips a run-state flag (running / stepping /
    // pauseRequested / cancelRequested) that status() projects but the ENGINE cannot see, so the engine's own
    // change signal would not cover it. Applied uniformly to every Submitted return — including the few verbs
    // that change nothing status() reports (setPauseOnError / setBreakpoints) — because over-announcing is free:
    // the push transport re-sends only when the serialized status actually differs from what it last sent, so a
    // redundant signal collapses to nothing. A uniform rule can't rot the way a per-verb audit would.
    private fun submitted(): LogicRunResponse {
        notifyStatusObservers()
        return LogicRunResponse.Submitted
    }


    // The control-verb preamble every verb shares: a settled (terminal-retained) run is not-found for
    // control — it exists only for trace review — and a stale run id refuses rather than driving a run the
    // caller has never seen. Callers unwrap with `when` and return [Refused.response] (wrapped as needed).
    private sealed interface ControlTarget {
        data class Active(val state: LogicState): ControlTarget
        data class Refused(val response: LogicRunResponse): ControlTarget
    }

    private fun controlTarget(runId: LogicRunId): ControlTarget {
        val state = stateOrNull?.takeIf { !it.settled }
            ?: return ControlTarget.Refused(LogicRunResponse.NotFound)

        if (state.runId != runId) {
            return ControlTarget.Refused(LogicRunResponse.RunIdMismatch)
        }

        return ControlTarget.Active(state)
    }


    // Every drive converges the same way (see the class KDoc): [engineWork] runs on the single-thread
    // driving executor — ending in an awaitQuiescent that blocks until the engine settles at its next
    // wavefront — then [settleAfterDrive] reflects the settled state back into the status flags under the
    // controller monitor.
    private fun driveAndSettle(state: LogicState, engineWork: (RunEngine) -> Unit) {
        executor.execute {
            engineWork(state.engine)
            synchronized(this@ServerLogicController) {
                settleAfterDrive(state)
            }
        }
    }

    // A notation edit MAY have landed since [pendingMigration] last reconciled — the cheap first stage of
    // live-edit detection (fed by the graphStore observer callbacks below; registered at the composition root).
    // Coarse by design (an edit to an unrelated document also sets it): the closure compare in
    // [pendingMigration] is the precise second stage; this flag only spares a clean release from recomputing
    // the transitive-closure notation map on every drive (slow motion pays that per tick).
    @Volatile
    private var editDirty = false

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ServerLogicController-execution").apply {
            isDaemon = true
        }
    }

    // The move-to per-hop capability gate (pure, no run-state mutation) — see [RepositionGate].
    private val repositionGate = RepositionGate(objectStableMapper)


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent,
        graphDefinition: GraphDefinitionAttempt,
        attachment: LocalGraphStore.Attachment
    ) {
        editDirty = true
    }


    override suspend fun onCommandFailure(
        command: NotationCommand,
        cause: Throwable,
        attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        editDirty = true
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override fun status(): LogicStatus {
        val state = stateOrNull
            ?: return LogicStatus(epoch, refreshStructureVersion(StructureSignature(epoch, null, null, null)), null)

        // A settled (terminal) run is retained only for post-run trace review — report it as no active run,
        // so the client stops driving it and storage-area eviction (which gates on active == null) runs.
        if (state.settled) {
            return LogicStatus(epoch, refreshStructureVersion(StructureSignature(epoch, null, null, null)), null)
        }

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

        val structureVersion = refreshStructureVersion(
            StructureSignature(epoch, state.runId, runState, collectNodeIds(snapshot.root)))

        // The root frame is the handle the client controls the run through, so it survives even a deleted root
        // document by falling back to the location the run was started from.
        val rootLocation = objectStableMapper.objectLocationOrNull(snapshot.root.stableId)
            ?: state.rootLocation

        // snapshot.sequence is the run's monotonic trace high-water: a client holding it has, by construction,
        // nothing newer to fetch — so it doubles as the run's cache version (see LogicRunInfo.sequence).
        return LogicStatus(
            epoch,
            structureVersion,
            LogicRunInfo(state.runId, nodeToFrame(snapshot.root, rootLocation), runState, snapshot.sequence))
    }

    // Bumps structureVersion iff the run's structure moved since the last status() (see the field doc). Called
    // only from status(), so it inherits the @Synchronized monitor — no double-count across concurrent callers.
    private fun refreshStructureVersion(signature: StructureSignature): Long {
        if (signature != lastStructureSignature) {
            structureVersion += 1
            lastStructureSignature = signature
        }
        return structureVersion
    }

    // Pre-order walk collecting EVERY node's id — including terminal children (unlike nodeToFrame) — so the
    // signature tracks the same execution set RunEngineLogicTrace.lookupRunExecutions projects to the client.
    private fun collectNodeIds(root: Node): List<String> {
        val result = mutableListOf<String>()
        fun visit(node: Node) {
            result.add(node.id.value)
            node.children.forEach { visit(it) }
        }
        visit(root)
        return result
    }


    @Synchronized
    override fun start(
        root: ObjectLocation,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunId? {
        return startAttempt(root, snapshotGraphDefinitionAttempt, false).runIdOrNull
    }


    @Synchronized
    fun startAttempt(
        root: ObjectLocation,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?,
        pauseOnError: Boolean
    ): LogicStartAttempt {
        val existing = stateOrNull
        if (existing != null) {
            if (!existing.settled) {
                // An active run is in progress — refuse (single-run controller; multi-run is a later phase).
                return LogicStartAttempt.Failed("A run is already in progress")
            }
            // The prior run is retained only for post-run trace review; a fresh run supersedes it — dispose it.
            // This is what wipes the old trace (replacing the former logicTraceStore.clearAll()).
            disposeState(existing)
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
                // Not a supported flavour, or the definition is incomplete. Fail gracefully (clean 400 naming
                // the reason) instead of letting it escape as a 500. A NotImplementedError (Error, not
                // Exception) is the unported-flavour signal, so catch Throwable.
                logger.warn("Unable to compile logic: {}", root, e)
                return LogicStartAttempt.Failed(
                    "Unable to compile ${root.asString()}: ${e.message ?: e::class.simpleName}")
            }

        val runId = runExecutionId.logicRunId

        // A fresh run has no carried state, so deletions recorded before it are irrelevant. Discarding them
        // here is also what keeps the mapper's removal record from growing across an editing session.
        objectStableMapper.drainRemovedIds()

        val rootStableId = objectStableMapper.objectStableId(root)
        val engine = RunEngine(logic, rootStableId)
        engine.pauseOnError(pauseOnError)

        // No trace-store clear / bridge wiring / eager root registration needed: the engine IS the trace store
        // now (served at query time by RunEngineLogicTrace), disposing the prior retained run above wipes the
        // old trace, and the engine's root node exists from construction — so a Job root (which only HOSTS its
        // Workers) is discoverable via mostRecent(root) the moment it starts, with no eager registration.
        val state = LogicState(
            runId, runExecutionId, engine, root,
            LinkedLogicDocuments.transitiveDigest(
                graphDefinitionAttempt.transitiveSuccessful,
                graphDefinitionAttempt.graphStructure,
                root.documentPath))
        stateOrNull = state

        // The controller's single subscription to this run's engine — re-broadcast to statusObservers, so a
        // consumer (the push transport) subscribes once to the controller and keeps working across runs.
        // The listener runs on an engine dispatcher thread on the hot path: it must stay this cheap.
        state.engineSubscription = engine.observe { notifyStatusObservers() }

        // A new run replaces whatever the client was projecting (including a just-disposed retained trace):
        // its runId differs, but epoch also covers the case where a consumer keys on more than the run.
        bumpEpoch()

        return LogicStartAttempt.Started(runId)
    }


    @Synchronized
    override fun request(
        runId: LogicRunId,
        executionId: LogicExecutionId,
        request: ExecutionRequest
    ): ExecutionResult {
        val state = stateOrNull?.takeIf { !it.settled }
            ?: return ExecutionResult.failure(LogicConventions.notRunningError())

        if (state.runId != runId) {
            return ExecutionResult.failure(
                LogicConventions.wrongRunningError(runId, state.runId))
        }

        return state.engine.request(NodeId(executionId.value), request)
    }


    @Synchronized
    override fun cancel(runId: LogicRunId): LogicRunResponse {
        val state = when (val target = controlTarget(runId)) {
            is ControlTarget.Refused -> return target.response
            is ControlTarget.Active -> target.state
        }

        state.cancelRequested = true

        // Signal directly (non-blocking) so the cancel reaches an in-flight run instead of queueing behind the
        // busy driving executor.
        state.engine.cancel()

        if (!state.running && !state.stepping) {
            // No in-flight drive task is waiting to observe the settle (the run was paused), so finalize the
            // now-cancelling run on the executor (which is free).
            driveAndSettle(state) { it.awaitQuiescent() }
        }

        return submitted()
    }


    @Synchronized
    override fun pause(runId: LogicRunId): LogicRunResponse {
        val state = when (val target = controlTarget(runId)) {
            is ControlTarget.Refused -> return target.response
            is ControlTarget.Active -> target.state
        }

        when {
            !state.launched -> {
                // Pause-at-entry: launch the run paused so it settles at the first step boundary (the first
                // step highlighted as "next to run"), matching the debugger's start-paused behaviour. A
                // subsequent step then runs that first step.
                state.launched = true
                state.running = true
                state.pauseRequested = true
                driveAndSettle(state) {
                    it.step(StepMode.Into)
                    it.awaitQuiescent()
                }
            }

            state.running || state.stepping -> {
                state.pauseRequested = true
                // Signal directly; the in-flight run settles at its next boundary and the driving task converges
                // it. Covers a long in-flight step too (e.g. a step-over of a sub-script): the engine's pause
                // overrides the stepping command, parking the run at its next boundary mid-step.
                state.engine.pause()
            }

            // else: already settled at a pause — nothing to do.
        }

        return submitted()
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
        val state = when (val target = controlTarget(runId)) {
            is ControlTarget.Refused -> return target.response
            is ControlTarget.Active -> target.state
        }

        check(!state.launched) { "Can't start stepping, already running" }
        check(!state.cancelRequested) { "Can't start stepping, cancel already requested" }

        state.launched = true
        state.stepping = true

        driveAndSettle(state) { engine ->
            // The first step launches the run parked at entry (before the first step; the launch is always
            // mode-agnostic — the never-started engine simply parks at the entry wavefront); the intermediate
            // quiesce lets it park so the second step has a parked node to drain — running that first step in
            // [mode] and parking before the next. Mirrors pause-at-entry followed by one Step [mode].
            engine.step(StepMode.Into)
            engine.awaitQuiescent()
            engine.step(mode)
            engine.awaitQuiescent()
        }

        return submitted()
    }


    // Live-toggle pause-on-error on the active run (the header toggle, clickable while paused). Takes effect at
    // the next boundary the execution checks.
    @Synchronized
    fun setPauseOnError(runId: LogicRunId, value: Boolean): LogicRunResponse {
        val state = when (val target = controlTarget(runId)) {
            is ControlTarget.Refused -> return target.response
            is ControlTarget.Active -> target.state
        }

        state.engine.pauseOnError(value)
        return submitted()
    }


    // Replace the run's breakpoint set (run-scoped, volatile — the client re-pushes at run start and on each
    // toggle). Locations resolve to stable ids here, so engine-side breakpoints survive rename; signal-only,
    // like [pause] — takes effect at the next named boundary any execution reaches.
    @Synchronized
    fun setBreakpoints(runId: LogicRunId, locations: List<ObjectLocation>): LogicRunResponse {
        val state = when (val target = controlTarget(runId)) {
            is ControlTarget.Refused -> return target.response
            is ControlTarget.Active -> target.state
        }

        state.engine.setBreakpoints(
            locations.map { objectStableMapper.objectStableId(it) }.toSet())
        return submitted()
    }


    @Synchronized
    override fun continueOrStart(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        val state = when (val target = controlTarget(runId)) {
            is ControlTarget.Refused -> return target.response
            is ControlTarget.Active -> target.state
        }

        check(!state.running && !state.stepping) { "Can't run, already running" }
        check(!state.cancelRequested) { "Can't run, cancel already requested" }

        val migration = pendingMigration(state, snapshotGraphDefinitionAttempt)
        val removedStableIds = migrationRemovals(migration)

        state.pauseRequested = false
        state.launched = true
        state.running = true

        driveAndSettle(state) { engine ->
            engine.awaitQuiescent()
            if (migration != null) {
                // The notation changed under the paused run: rebuild from the edit and run free (the migrate
                // carries each flavour's surviving state across by stable id).
                engine.migrate(migration, paused = false, removedStableIds = removedStableIds)
            }
            else {
                engine.resume()
            }
            engine.awaitQuiescent()
        }

        return submitted()
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


    /**
     * Move-to (Set Next Statement): reposition a settled run's pointer to [target] without executing the
     * intervening steps — backward = re-run from the target, forward = skip over. Realised as a self-migration
     * carrying the request through the engine barrier (execution-control phase 2).
     *
     * [executionId] names the FRAME the request addresses — null = the run's root frame, which is the whole
     * behaviour when nothing is nested. The frame's chain of call-sites is resolved from the engine's live node
     * tree and travels with the target, so a move inside a hosted sub-Logic reaches that one invocation: under
     * recursion the same target id is live in several frames at once, and only the addressed one may move.
     * Whether every frame on the path can honour its own role is gated by [RepositionGate].
     *
     * Unlike step / resume, a jump ALWAYS recompiles from the current notation (a jump is itself a migrate — it
     * shares the barrier with any concurrent edit, so an edit-then-jump takes both in one rebuild) and is
     * refusable: an unknown or already-settled frame, a hop that cannot carry its role, an unsupported /
     * structurally-invalid target, or a recompile failure returns [LogicRunResponse.Rejected] with the run left
     * untouched. Every refusal names its reason in [LogicControlReply.reason], which the client shows beside the
     * rejection, so a move the user cannot make is never silently dropped. Allowed while paused OR error-parked
     * (jumping PAST a failing step is a headline use case); rejected while running.
     */
    @Synchronized
    fun moveToAttempt(
        runId: LogicRunId,
        target: ObjectLocation,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null,
        executionId: LogicExecutionId? = null
    ): LogicControlReply {
        val state = when (val controlTarget = controlTarget(runId)) {
            is ControlTarget.Refused -> return LogicControlReply(controlTarget.response)
            is ControlTarget.Active -> controlTarget.state
        }

        check(!state.running && !state.stepping) { "Can't move, already running" }
        check(!state.cancelRequested) { "Can't move, cancel already requested" }
        check(state.launched) { "Can't move an unlaunched run" }

        val targetId = objectStableMapper.objectStableId(target)

        val framePath = repositionGate.framePathTo(state.engine.snapshot().root, executionId)
            ?: return rejected("That run frame is no longer active")
        val addressedNode = framePath.last()

        // [nodeToFrame] prunes terminal children from the wire tree but the engine snapshot keeps them, so a
        // client poll that raced the frame's settle can still name it. Nothing would honour the request: the
        // migrate would rebuild and the move would be a silent no-op.
        if (addressedNode.status is NodeStatus.Terminal) {
            return rejected("That run frame has already finished")
        }

        // No-op guard: the ADDRESSED frame is already parked at the target — a rebuild is not free and is lossy
        // (the coalescing note). Read off that frame and never the root: while a run is parked inside a child,
        // the root node is Running (it is blocked in `host`; only a park sets Suspended), so a root-read guard
        // could never fire for a nested move.
        if (addressedNode.status is NodeStatus.Suspended && addressedNode.position == targetId) {
            return LogicControlReply(submitted())
        }

        // A jump ALWAYS recompiles from the current notation (decision 10), updating the closure baseline like
        // pendingMigration — so an edit-then-jump takes both in one rebuild. A recompile failure is refusable
        // (unlike pendingMigration's keep-running fallback): return Rejected, run untouched (nothing torn down).
        val (attempt, logic) = try {
            val attempt = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)
            val editedDigest = LinkedLogicDocuments.transitiveDigest(
                attempt.transitiveSuccessful, attempt.graphStructure, state.rootLocation.documentPath)
            val compiled = compileLogic(state.rootLocation, attempt, state.runExecutionId)
            state.baselineClosureDigest = editedDigest
            editDirty = false
            attempt to compiled
        }
        catch (e: Throwable) {
            logger.warn("Unable to recompile for move-to, keeping prior definition: {}", state.rootLocation, e)
            return rejected(
                "Unable to compile ${state.rootLocation.documentPath.name.value}: " +
                        (e.message ?: e::class.simpleName))
        }

        val moveTarget =
            when (val request = repositionGate.repositionRequest(
                    framePath, state.rootLocation, logic,
                    { hopLocation -> compileLogic(hopLocation, attempt, state.runExecutionId) },
                    targetId)) {
                is RepositionGate.Attempt.Refused -> return rejected(request.reason)
                is RepositionGate.Attempt.Accepted -> request.moveTarget
            }

        val removedStableIds = objectStableMapper.drainRemovedIds()

        state.pauseRequested = false
        state.stepping = true

        driveAndSettle(state) { engine ->
            engine.awaitQuiescent()
            engine.migrate(
                logic, paused = true, moveTarget = moveTarget, removedStableIds = removedStableIds)
            engine.awaitQuiescent()
        }

        return LogicControlReply(submitted())
    }


    // For the callers that only need "did it land" — the tests driving a run. The reason a refusal names is
    // what the client shows, so anything user-facing goes through [moveToAttempt].
    fun moveTo(
        runId: LogicRunId,
        target: ObjectLocation,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null,
        executionId: LogicExecutionId? = null
    ): LogicRunResponse {
        return moveToAttempt(runId, target, snapshotGraphDefinitionAttempt, executionId).response
    }


    private fun rejected(reason: String): LogicControlReply {
        return LogicControlReply(LogicRunResponse.Rejected, reason)
    }


    private fun drive(
        runId: LogicRunId,
        mode: StepMode,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        val state = when (val target = controlTarget(runId)) {
            is ControlTarget.Refused -> return target.response
            is ControlTarget.Active -> target.state
        }

        check(!state.running && !state.stepping) { "Can't step, already running" }
        check(!state.cancelRequested) { "Can't step, cancel already requested" }

        val migration = pendingMigration(state, snapshotGraphDefinitionAttempt)
        val removedStableIds = migrationRemovals(migration)

        state.pauseRequested = false
        state.launched = true
        state.stepping = true

        driveAndSettle(state) { engine ->
            engine.awaitQuiescent()
            if (migration != null) {
                // Step after an edit: rebuild from the edit and park at the new definition's first wavefront.
                engine.migrate(migration, paused = true, removedStableIds = removedStableIds)
            }
            else {
                engine.step(mode)
            }
            engine.awaitQuiescent()
        }

        return submitted()
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

        // The settle is invisible to the engine's own change signal — the engine published its park while
        // `stepping` was still set, so that publish reported Stepping, not Paused. Announce it here (for
        // every settle, not just the terminal one below) or the Stepping -> Paused transition never pushes;
        // see architecture.md §3, pinned by ServerLogicControllerStatusObserverTest.
        notifyStatusObservers()

        val rootStatus = state.engine.snapshot().root.status
        if (rootStatus is NodeStatus.Terminal) {
            // Retain the settled run for post-run trace queries: stop the engine's pools (no threads held) but
            // keep its node tree + history readable. The next start() (or clearRetainedTrace) disposes it;
            // status() reports it as no-active-run via `settled`.
            state.settled = true
            state.engine.shutdown()

            // status() now reports no-active-run, so the run's own sequence disappears from the wire — the epoch
            // is what tells a client the terminal transition happened and its final trace is ready to pull.
            bumpEpoch()
        }
    }


    // Fully dispose a run (active or retained) and forget it. Used when a fresh run supersedes a retained one,
    // on the global "Clear all traces", and at controller close.
    private fun disposeState(state: LogicState) {
        if (stateOrNull === state) {
            stateOrNull = null
        }

        // Drop our engine subscription BEFORE disposing: neither shutdown() nor dispose() clears the engine's
        // observer list, so an unclosed listener would outlive the run it was created for.
        state.engineSubscription?.close()
        state.engineSubscription = null

        state.engine.dispose()
    }


    // Trace-query access to the current / most-recently-settled run's engine, for [RunEngineLogicTrace] — the
    // engine IS the trace store now (served by projecting it at query time), so there is no per-node bridge.
    // Returns the retained state whether active or settled (post-run review reads a terminal engine too); null
    // when no run has started this process life. Read under the controller lock; the caller then reads the
    // engine off-lock (the engine has its own lock).
    @Synchronized
    fun retainedTraceAccess(): RunTraceAccess? {
        val state = stateOrNull
            ?: return null
        return RunTraceAccess(state.runId, state.engine)
    }


    // Dispose the retained run entirely — the "Clear all traces" action. A no-op while a run is active (the UI
    // disables the control then); returns whether a retained run was cleared.
    @Synchronized
    fun clearRetainedTrace(): Boolean {
        val state = stateOrNull
            ?: return false
        if (!state.settled) {
            return false
        }
        disposeState(state)

        // Load-bearing: status() reports active == null both before and after this clear, so without the
        // epoch bump the wire response is byte-identical and no trace view ever repaints to empty (see [epoch]).
        bumpEpoch()
        return true
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
    // (step-over / step-out) doesn't count toward the paused stack depth.
    //
    // Every stable id here is resolved leniently, because a run keeps executing while its notation is edited:
    // deleting the step a paused run is parked at drops that id from [ObjectStableMapper], and a throw would
    // take out both /logic/status and the SSE loop that re-serializes it, wedging the client out of the very
    // step that would migrate the run past the deletion. An unresolvable position reports none — the truth
    // once the next-to-run step is gone — and an unresolvable child frame is pruned, like [RunEngineLogicTrace].
    private fun nodeToFrame(node: Node, objectLocation: ObjectLocation): LogicRunFrameInfo {
        return LogicRunFrameInfo(
            objectLocation,
            LogicExecutionId(node.id.value),
            node.children
                .filter { it.status !is NodeStatus.Terminal }
                .mapNotNull { nodeToFrameOrNull(it) },
            node.position?.let { objectStableMapper.objectLocationOrNull(it) })
    }


    private fun nodeToFrameOrNull(node: Node): LogicRunFrameInfo? {
        val objectLocation = objectStableMapper.objectLocationOrNull(node.stableId)
            ?: return null
        return nodeToFrame(node, objectLocation)
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
                environment, objectStableMapper, cachedKotlinCompiler, scriptValidationCache,
                jobValidationCache, notationMetadataReader, jobWorkPool, runExecutionId))
    }


    // The elements deleted since the last barrier, for the migrate that is about to consume them (see
    // [RunEngine.migrate]). Claimed only when a rebuild actually happens: a release that keeps the current
    // definition running must leave them pending for the barrier that does rebuild, or the run would carry a
    // deleted step's outcome onto whatever the user creates in its place.
    private fun migrationRemovals(migration: Logic?): Set<ObjectStableId> {
        return when (migration) {
            null -> emptySet()
            else -> objectStableMapper.drainRemovedIds()
        }
    }


    // The recompiled root [Logic] to migrate the live run onto when its notation changed under a live edit, or
    // null to resume / step the existing tree. The change detection is two-stage: the cheap [editDirty] flag
    // (event-driven, coarse — set by any notation command) gates the precise transitive-closure content-digest
    // compare ([LinkedLogicDocuments.transitiveDigest] — root document ∪ linked logic documents — vs
    // [LogicState.baselineClosureDigest]) — deterministic, so a
    // no-edit release never migrates, and a clean release skips the closure recompute entirely. Only a LAUNCHED
    // run migrates — an unlaunched engine has no live state to re-point, so the first release just runs the
    // start-time logic. A recompile failure (a mid-edit incomplete definition), or any failure recomputing the
    // closure digest, falls back to null — keeping the prior definition running rather than killing the run.
    private fun pendingMigration(
        state: LogicState,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): Logic? {
        if (!state.launched) {
            return null
        }

        if (!editDirty) {
            return null
        }
        // Clear BEFORE reconciling: an edit landing mid-compare re-sets it, so the next drive re-checks.
        editDirty = false

        return try {
            val attempt = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)
            val editedDigest = LinkedLogicDocuments.transitiveDigest(
                attempt.transitiveSuccessful, attempt.graphStructure, state.rootLocation.documentPath)
            if (editedDigest == state.baselineClosureDigest) {
                return null
            }

            val logic = compileLogic(state.rootLocation, attempt, state.runExecutionId)
            state.baselineClosureDigest = editedDigest
            logic
        }
        catch (e: Throwable) {
            // Keep the prior definition running, but stay dirty so the next release retries the reconcile.
            editDirty = true
            logger.warn("Unable to recompile edited logic, keeping prior definition: {}", state.rootLocation, e)
            null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        closeAndJoin()
    }


    /**
     * Cancels the active run and joins the driving executor; false when the join timed out and the run was
     * interrupted instead — the owner then must not treat the work root as safely reusable.
     */
    fun closeAndJoin(): Boolean {
        synchronized(this) {
            stateOrNull?.engine?.cancel()
        }

        executor.shutdown()
        val joined = executor.awaitTermination(closeJoinTimeoutSeconds, TimeUnit.SECONDS)
        if (!joined) {
            executor.shutdownNow()
        }

        synchronized(this) {
            stateOrNull?.let { disposeState(it) }
        }
        return joined
    }
}
