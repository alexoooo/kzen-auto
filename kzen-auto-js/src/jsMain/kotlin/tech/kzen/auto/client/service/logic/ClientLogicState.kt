package tech.kzen.auto.client.service.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.run.model.LogicStatus
import tech.kzen.lib.common.service.store.normal.ObjectStableId


data class ClientLogicState(
    val logicStatus: LogicStatus? = null,
    val pending: Pending = Pending.Initialize,
    val controlError: String? = null,

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


    // Monotone version of everything the trace / progress / inventory views project off a run. THE fetch key for
    // every such view — one rule here rather than a per-flavour notion of "changed".
    //
    // Changes only when something observable actually changed, so an idle or paused run stops re-fetching
    // entirely. This replaces the retired LogicStatus.time, which was a wall clock stamped per status call and
    // therefore ALWAYS differed — silently forcing every Logic document to re-pull its full trace snapshot on
    // every poll (~4 detached calls for a Script, 1-2 Flow, 2 Job, at 1.5s forever, and again per 50ms
    // slow-motion settle poll).
    //
    // The three components each carry something the others cannot:
    //   - epoch    — run started / settled terminal / retained trace cleared. Present even with no active run,
    //                which is what makes a post-run "Clear all traces" repaint views to empty.
    //   - id       — a different run is a different projection.
    //   - sequence — the run's trace high-water: new values exist iff it advanced.
    //   - state    — Running/Paused/Stepping/... can change with no new trace event (e.g. Pausing -> Paused).
    fun traceVersion(): String {
        val status = logicStatus
            ?: return noTraceVersion

        val active = status.active
            ?: return "e${status.epoch}"

        return "e${status.epoch}|${active.id.value}|${active.sequence}|${active.state.name}"
    }
}
