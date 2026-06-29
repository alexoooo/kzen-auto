package tech.kzen.auto.client.service.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.run.model.LogicStatus


data class ClientLogicState(
    val logicStatus: LogicStatus? = null,
    val pending: Pending = Pending.Initialize,
    val controlError: String? = null,

    // True while a client-paced "slow motion" auto-step loop is driving this run (see
    // ClientLogicGlobal.slowRunAsync). Surfaced so the run controls can render the loop's on/off state.
    val slowLooping: Boolean = false,

    // When slowLooping, whether the loop auto-issues Step Over (stays within the current document)
    // instead of Step (descends into nested logic). Only meaningful while slowLooping.
    val slowStepOver: Boolean = false
) {
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
}
