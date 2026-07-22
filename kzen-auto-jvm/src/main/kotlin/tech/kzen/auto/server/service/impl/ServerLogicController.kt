package tech.kzen.auto.server.service.impl

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.logic.LogicConventions
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
import tech.kzen.lib.common.exec.engine.Repositionable
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
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.server.exec.engine.RunEngine
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


/**
 * Drives a single run on the new single-writer [RunEngine] (logic-spec greenfield core), while keeping the
 * existing [LogicController] REST contract intact so the client (status / start / run / step / pause / cancel)
 * is unchanged. The root document is translated to an engine [tech.kzen.lib.common.exec.engine.Logic] by
 * [LogicCompiler]. The engine IS the trace store: this controller no longer mirrors trace events anywhere;
 * the REST trace surface is served by projecting the run's engine at query time
 * ([tech.kzen.auto.server.exec.RunEngineLogicTrace], reachable via [retainedTraceAccess]). A settled run's
 * engine is RETAINED (pools stopped via [RunEngine.shutdown], tree + history kept readable) for post-run
 * review; the next [start] (or a global clear via [clearRetainedTrace]) disposes it. [status] reports a
 * settled run as no-active-run.
 *
 * Script, Flow, Job and Report documents all compile onto the engine now (a document of any other type fails to
 * compile → clean 400). The Job port runs its Workers as concurrent confined child nodes; a Report runs its
 * record pipeline on the run's root node — each flavour's per-element / progress values reach the JS UI through
 * the shared query-time projection, with no per-flavour code here.
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
 * edit takes effect on the live run. Detection is event-driven: this controller observes the graph store (as a
 * [LocalGraphStore.Observer], registered at the composition root) to set a coarse edit-dirty flag, so a clean
 * release skips the closure compare entirely (slow motion would otherwise pay it per tick). The compared
 * closure spans the root document PLUS its linked logic documents (a RunStep sub-script, a Flow RunLogic
 * callee, a Job RunWorker callee — recursively, discovered from notation by [LinkedLogicDocuments]), so
 * editing a paused caller's callee migrates the caller even though the `instructions` link is weak. This is
 * flavour-agnostic: a Job carries its Worker state + channel carryover across the rebuild (see
 * [tech.kzen.auto.server.exec.job.JobRun]); a Script carries its completed step outcomes + result (see
 * [tech.kzen.auto.server.exec.script.ScriptMigrationState]) — and the engine itself carries open resource
 * registrations (a browser) by owning frame; a Flow registers no capture yet, so it cleanly restarts on the
 * edited definition (the safe best-effort §5 default).
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

    // Version of everything a trace/progress consumer projects that the run's own trace sequence CANNOT express:
    // a run started, a run settled terminal, a retained trace was cleared. Bumped under the controller lock;
    // surfaced as LogicStatus.epoch, and deliberately bumps even while there is no active run — that is what lets
    // a client notice a post-run "clear traces" (before and after, status() reports active == null, so an
    // active-run-derived version alone would be identical across the clear and no view would ever repaint to
    // empty). Replaces the retired LogicStatus.time wall clock, which conveyed this by being fresh on EVERY call
    // — and therefore forced every consumer to re-fetch its full trace snapshot on every poll.
    private var epoch: Long = 0

    // Surfaced as LogicStatus.structureVersion: a monotone counter that moves only on a genuine EXECUTION-TREE
    // change — an execution created/destroyed, a run-state transition, or a run lifecycle/clear event — but
    // NOT on a plain trace emit (which advances only the run's sequence). It is what lets a structure-keyed
    // consumer (the traced-document set, the execution tree) re-fetch ~15-17x/run instead of once per publish.
    //
    // Computed LAZILY in status() (under this controller's monitor, off the engine hot path) by comparing a
    // cheap signature against the last: epoch is folded in (so all three bumpEpoch transitions ride into it),
    // runState catches state transitions, and the unfiltered node-id set catches execution create/destroy.
    // Deliberately no reactive bump sites of its own — every structural mutation already fires
    // notifyStatusObservers(), which pulls a status() that observes the change; conflation of the push channel
    // can only COLLAPSE bumps, which is safe because the structure-keyed queries are full snapshots, not deltas.
    private var structureVersion: Long = 0
    private var lastStructureSignature: StructureSignature? = null

    // The value status() diffs to decide whether structureVersion moved. nodeIds is the UNFILTERED set of
    // execution-node ids under snapshot.root (mirrors RunEngineLogicTrace's execution walk, NOT the terminal-
    // pruned nodeToFrame): a child hosted and run to completion inside one Step-Over stays in the execution
    // tree though it leaves the live frame, so a frame-derived set would let the client's execution tree go
    // stale. Node ids are monotone (n0, n1, ...) and never revisited, so equal signatures ⇒ identical trees.
    private data class StructureSignature(
        val epoch: Long,
        val runId: LogicRunId?,
        val runState: LogicRunState?,
        val nodeIds: List<String>?)

    // Consumers of "the run status may have changed" — the push transport (/logic/events) is the only one.
    // Payload-free, mirroring the engine's own Run.observe contract: a listener is told THAT something changed
    // and pulls status() itself.
    //
    // Deliberately controller-scoped rather than one engine subscription per consumer: the engine a consumer
    // would subscribe to is replaced on each start() and disposed on clear, and the engine never clears its
    // observer list on shutdown()/dispose() — so per-consumer engine subscriptions would both miss the run they
    // care about and accumulate. The controller holds exactly one subscription per run (LogicState
    // .engineSubscription) and fans out from here; it also owns the epoch transitions no engine can see.
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

        // snapshot.sequence is the run's monotonic trace high-water: a client holding it has, by construction,
        // nothing newer to fetch — so it doubles as the run's cache version (see LogicRunInfo.sequence).
        return LogicStatus(
            epoch,
            structureVersion,
            LogicRunInfo(state.runId, nodeToFrame(snapshot.root), runState, snapshot.sequence))
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
        // A settled (terminal-retained) run is not-found for control — it exists only for trace review.
        val state = stateOrNull?.takeIf { !it.settled }
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
                    settleAfterDrive(state)
                }
            }
        }

        return submitted()
    }


    @Synchronized
    override fun pause(runId: LogicRunId): LogicRunResponse {
        // A settled (terminal-retained) run is not-found for control — it exists only for trace review.
        val state = stateOrNull?.takeIf { !it.settled }
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
        // A settled (terminal-retained) run is not-found for control — it exists only for trace review.
        val state = stateOrNull?.takeIf { !it.settled }
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

        return submitted()
    }


    // Live-toggle pause-on-error on the active run (the header toggle, clickable while paused). Takes effect at
    // the next boundary the execution checks.
    @Synchronized
    fun setPauseOnError(runId: LogicRunId, value: Boolean): LogicRunResponse {
        // A settled (terminal-retained) run is not-found for control — it exists only for trace review.
        val state = stateOrNull?.takeIf { !it.settled }
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        state.engine.pauseOnError(value)
        return submitted()
    }


    // Replace the run's breakpoint set (run-scoped, volatile — the client re-pushes at run start and on each
    // toggle). Locations resolve to stable ids here, so engine-side breakpoints survive rename; signal-only,
    // like [pause] — takes effect at the next named boundary any execution reaches.
    @Synchronized
    fun setBreakpoints(runId: LogicRunId, locations: List<ObjectLocation>): LogicRunResponse {
        // A settled (terminal-retained) run is not-found for control — it exists only for trace review.
        val state = stateOrNull?.takeIf { !it.settled }
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
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
        // A settled (terminal-retained) run is not-found for control — it exists only for trace review.
        val state = stateOrNull?.takeIf { !it.settled }
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
     * carrying the target through the engine barrier (execution-control phase 2); only a [Repositionable] root
     * Logic whose structure resolves the target honours it. Unlike step / resume, a jump ALWAYS recompiles from
     * the current notation (a jump is itself a migrate — it shares the barrier with any concurrent edit, so an
     * edit-then-jump takes both in one rebuild) and is refusable: an unsupported / structurally-invalid target,
     * or a recompile failure, returns [LogicRunResponse.Rejected] with the run left untouched. Allowed while
     * paused OR error-parked (jumping PAST a failing step is a headline use case); rejected while running.
     */
    @Synchronized
    fun moveTo(
        runId: LogicRunId,
        target: ObjectLocation,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null
    ): LogicRunResponse {
        // A settled (terminal-retained) run is not-found for control — it exists only for trace review.
        val state = stateOrNull?.takeIf { !it.settled }
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.RunIdMismatch
        }

        check(!state.running && !state.stepping) { "Can't move, already running" }
        check(!state.cancelRequested) { "Can't move, cancel already requested" }
        check(state.launched) { "Can't move an unlaunched run" }

        val targetId = objectStableMapper.objectStableId(target)

        // No-op guard: already parked at the target — a rebuild is not free and is lossy (the coalescing note).
        val rootNode = state.engine.snapshot().root
        if (rootNode.status is NodeStatus.Suspended && rootNode.position == targetId) {
            return submitted()
        }

        // A jump ALWAYS recompiles from the current notation (decision 10), updating the closure baseline like
        // pendingMigration — so an edit-then-jump takes both in one rebuild. A recompile failure is refusable
        // (unlike pendingMigration's keep-running fallback): return Rejected, run untouched (nothing torn down).
        val logic = try {
            val attempt = graphDefinitionAttempt(snapshotGraphDefinitionAttempt)
            val editedDigest = LinkedLogicDocuments.transitiveDigest(
                attempt.transitiveSuccessful, attempt.graphStructure, state.rootLocation.documentPath)
            val compiled = compileLogic(state.rootLocation, attempt, state.runExecutionId)
            state.baselineClosureDigest = editedDigest
            editDirty = false
            compiled
        }
        catch (e: Throwable) {
            logger.warn("Unable to recompile for move-to, keeping prior definition: {}", state.rootLocation, e)
            return LogicRunResponse.Rejected
        }

        // Capability gate: reject an unsupported flavour or a structurally-invalid target (loop body / binding /
        // unknown id) BEFORE the executor tears anything down — the run keeps its current state.
        if (logic !is Repositionable || ! logic.canMoveTo(targetId)) {
            return LogicRunResponse.Rejected
        }

        state.pauseRequested = false
        state.stepping = true

        executor.execute {
            state.engine.awaitQuiescent()
            state.engine.migrate(logic, paused = true, moveTarget = targetId)
            state.engine.awaitQuiescent()
            synchronized(this@ServerLogicController) {
                settleAfterDrive(state)
            }
        }

        return submitted()
    }


    private fun drive(
        runId: LogicRunId,
        mode: StepMode,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt?
    ): LogicRunResponse {
        // A settled (terminal-retained) run is not-found for control — it exists only for trace review.
        val state = stateOrNull?.takeIf { !it.settled }
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
                // Step after an edit: rebuild from the edit and park at the new definition's first wavefront.
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

        // The settle is invisible to the engine's own change signal, so it MUST be announced here: the engine
        // published its park BEFORE this runs (we only get here once awaitQuiescent returned), and at that
        // moment `stepping` was still set — so that publish reported Stepping, not Paused. Without this notify
        // the Stepping -> Paused transition — precisely what an interactive client and the slow-motion loop
        // wait on — would never be pushed, and the UI would sit on "Stepping" until the fallback poll.
        // Announced for every settle, not just the terminal one below (which also bumps the epoch).
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

        // The load-bearing epoch bump: status() reported active == null before this clear and reports
        // active == null after it, so WITHOUT this the wire response is byte-identical across the clear and no
        // trace view would ever repaint to empty (this is precisely what the retired `time` wall clock used to
        // convey by accident, by being fresh per call).
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
    private fun nodeToFrame(node: Node): LogicRunFrameInfo {
        return LogicRunFrameInfo(
            objectStableMapper.objectLocation(node.stableId),
            LogicExecutionId(node.id.value),
            node.children
                .filter { it.status !is NodeStatus.Terminal }
                .map { nodeToFrame(it) },
            node.position?.let { objectStableMapper.objectLocation(it) })
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
        synchronized(this) {
            stateOrNull?.engine?.cancel()
        }

        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }

        synchronized(this) {
            stateOrNull?.let { disposeState(it) }
        }
    }
}
