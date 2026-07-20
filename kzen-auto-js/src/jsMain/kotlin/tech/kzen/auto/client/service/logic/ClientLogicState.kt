package tech.kzen.auto.client.service.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.run.model.LogicStatus
import tech.kzen.lib.common.service.store.normal.ObjectStableId


data class ClientLogicState(
    val logicStatus: LogicStatus? = null,
    val pending: Pending = Pending.Initialize,
    val controlError: ControlError? = null,

    // Breakpoint elements, keyed by the CLIENT-side stable id (rename-tracked by ObjectStableMapper, so a
    // dot follows its renamed step). One flat set across documents: rendering is a membership test, and the
    // whole set is pushed to the server (as current locations) on each toggle and at run start.
    val breakpoints: Set<ObjectStableId> = setOf(),

    // True while a client-paced "slow motion" auto-step loop is driving this run (see
    // ClientLogicGlobal.slowRunAsync). Surfaced so the run controls can render the loop's on/off state.
    val slowLooping: Boolean = false,

    // When slowLooping, whether the loop auto-issues Step Over (stays within the current document)
    // instead of Step (descends into nested logic). Only meaningful while slowLooping.
    val slowStepOver: Boolean = false
) {
    companion object {
        // traceVersion() before any status has been received. A distinguishable sentinel — no real status can
        // produce it — so a consumer can test "have we ever seen a status?" (the role Instant.DISTANT_PAST
        // played against the retired LogicStatus.time).
        const val noTraceVersion = "none"
    }


    enum class Pending {
        Initialize,
        Start,
        Cancel,
        Pause,
        Step,
        None
    }


    fun isActive(): Boolean {
        return logicStatus?.active != null
    }


    fun isExecuting(): Boolean {
        return logicStatus?.active?.state?.isExecuting() ?: false
    }


    // True while the run is settled at a deliberate halt — a Pause step (ExplicitPaused) or pause-on-error
    // (ErrorPaused). The slow-motion auto-step loop honours these by stopping, vs a plain Paused boundary
    // settle (its own per-step pause) which it advances through.
    fun isHaltPaused(): Boolean {
        val state = logicStatus?.active?.state
        return state == LogicRunState.ExplicitPaused || state == LogicRunState.ErrorPaused
    }


    // Monotone version of everything the trace / progress / inventory views project off a run, at PER-EMIT
    // granularity. THE fetch key for any view whose answer can change on a new trace value (lookup,
    // lookupRunHistory, the run-merged snapshot) — one rule here rather than a per-flavour notion of "changed".
    //
    // Changes only when something observable actually changed, so an idle or paused run stops re-fetching
    // entirely. This replaces the retired LogicStatus.time, which was a wall clock stamped per status call and
    // therefore ALWAYS differed — silently forcing every Logic document to re-pull its full trace snapshot on
    // every poll (~4 detached calls for a Script, 1-2 Flow, 2 Job, at 1.5s forever, and again per 50ms
    // slow-motion settle poll).
    //
    // Composed as the server structure version plus the run's per-emit trace high-water:
    //   - structureVersion — server-computed (LogicStatus.structureVersion): moves on every structural
    //                        transition (run started / settled / state change / execution created-destroyed /
    //                        trace cleared) but not on a plain position advance. Present even with no active
    //                        run, which is what makes a post-run "Clear all traces" repaint views to empty.
    //   - sequence         — the run's per-emit high-water: new trace values exist iff it advanced. A plain
    //                        Script advancing its position bumps only this, and those views must still re-fetch.
    // A superset of the retired epoch|id|state string: structureVersion embeds run identity and state, so a
    // state transition that shares a sequence with the prior status (Stepping -> Paused at settle) is caught.
    fun traceVersion(): String {
        val status = logicStatus
            ?: return noTraceVersion

        val active = status.active
            ?: return "s${status.structureVersion}"

        return "s${status.structureVersion}|${active.sequence}"
    }


    // The server's structural version verbatim: WHICH run, in WHAT state, with WHAT execution tree. The fetch
    // key for any view whose answer changes ONLY on structure (the traced-document set, the execution tree), so
    // it stops re-fetching per emit. Also how ClientLogicGlobal.publishStatus tells "the run reached a new
    // state" (started, settled, a step boundary, a descent into a child execution, a trace cleared) — a
    // transition the user is waiting on, published at once — apart from "the run emitted another value", which
    // is throttled because values arrive ~3.4/s and nobody can read them at that rate.
    //
    // Unlike E5's client-derived epoch|id|state, this now MOVES on an execution create/destroy: a stepped-over
    // RunStep descending into its child bumps it, so that intra-step repaint fires immediately again (TP4). It
    // still does NOT move on a plain frame-position advance within a stable execution set — a plain run stays
    // throttled — because the server computes exactly that distinction (see LogicStatus.structureVersion).
    fun structureVersion(): String {
        val status = logicStatus
            ?: return noTraceVersion

        return "s${status.structureVersion}"
    }
}
