package tech.kzen.auto.common.paradigm.common.v1.model


data class LogicRunExecutionId(
    val logicRunId: LogicRunId,
    val logicExecutionId: LogicExecutionId
) {
    companion object {
        fun ofCollection(collection: Map<String, String>): LogicRunExecutionId {
            return LogicRunExecutionId(
                LogicRunId(collection[LogicConventions.paramRunId]!!),
                LogicExecutionId(collection[LogicConventions.paramExecution]!!)
            )
        }
    }


    fun asCollection(): Map<String, String> {
        return mapOf(
            LogicConventions.paramRunId to logicRunId.value,
            LogicConventions.paramExecution to logicExecutionId.value
        )
    }
}