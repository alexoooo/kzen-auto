package tech.kzen.auto.common.paradigm.logic.run.model

import tech.kzen.auto.common.api.CommonRestApi


data class LogicRunExecutionId(
    val logicRunId: LogicRunId,
    val logicExecutionId: LogicExecutionId
) {
    companion object {
        fun ofCollection(collection: Map<String, String>): LogicRunExecutionId {
            return LogicRunExecutionId(
                LogicRunId(collection[CommonRestApi.paramRunId]!!),
                LogicExecutionId(collection[CommonRestApi.paramExecutionId]!!)
            )
        }
    }


    fun asCollection(): Map<String, String> {
        return mapOf(
            CommonRestApi.paramRunId to logicRunId.value,
            CommonRestApi.paramExecutionId to logicExecutionId.value
        )
    }
}