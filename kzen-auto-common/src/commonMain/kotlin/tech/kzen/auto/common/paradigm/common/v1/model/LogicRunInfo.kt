package tech.kzen.auto.common.paradigm.common.v1.model


data class LogicRunInfo(
    val id: LogicRunId,

    val frame: LogicRunFrameInfo,

    val state: LogicRunState
) {
    companion object {
        private const val idKey = "id"
        private const val frameKey = "frame"
        private const val stateKey = "state"

        fun ofCollection(collection: Map<String, Any>): LogicRunInfo {
            @Suppress("UNCHECKED_CAST")
            val frame = LogicRunFrameInfo.ofCollection(
                collection[frameKey] as Map<String, Any>)

            return LogicRunInfo(
                LogicRunId(collection[idKey] as String),
                frame,
                LogicRunState.valueOf(collection[stateKey] as String)
            )
        }
    }

    fun toCollection(): Map<String, Any> {
        return mapOf(
            idKey to id.value,
            frameKey to frame.toCollection(),
            stateKey to state.name
        )
    }
}