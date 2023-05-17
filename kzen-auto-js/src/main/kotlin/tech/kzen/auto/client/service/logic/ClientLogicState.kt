package tech.kzen.auto.client.service.logic

import tech.kzen.auto.common.paradigm.common.v1.model.LogicStatus


data class ClientLogicState(
    val logicStatus: LogicStatus? = null,
    val pending: Pending = Pending.Initialize,
    val controlError: String? = null
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
        return logicStatus?.active !=  null
    }
}
