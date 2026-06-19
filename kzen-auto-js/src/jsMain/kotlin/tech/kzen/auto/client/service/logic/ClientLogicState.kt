package tech.kzen.auto.client.service.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicStatus


data class ClientLogicState(
    val logicStatus: LogicStatus? = null,
    val pending: Pending = Pending.Initialize,
    val controlError: String? = null,

    // True while a client-paced "slow motion" auto-step loop is driving this run (see
    // ClientLogicGlobal.slowRunAsync). Surfaced so the run controls can render the loop's on/off state.
    val slowLooping: Boolean = false
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
}
