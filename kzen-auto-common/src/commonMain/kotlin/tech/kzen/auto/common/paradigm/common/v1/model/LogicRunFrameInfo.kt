package tech.kzen.auto.common.paradigm.common.v1.model

import tech.kzen.lib.common.model.locate.ObjectLocation


data class LogicRunFrameInfo(
    val objectLocation: ObjectLocation,
    val executionId: LogicExecutionId,
//    var state: LogicRunFrameState,
    val dependencies: List<LogicRunFrameInfo>,
) {
    companion object {
        private const val locationKey = "location"
        private const val executionKey = "execution"
//        private const val stateKey = "state"
        private const val dependenciesKey = "dependencies"

        fun ofCollection(collection: Map<String, Any>): LogicRunFrameInfo {
            @Suppress("UNCHECKED_CAST")
            val dependenciesValue = collection[dependenciesKey] as List<Map<String, Any>>

            return LogicRunFrameInfo(
                ObjectLocation.parse(collection[locationKey] as String),
                LogicExecutionId(collection[executionKey] as String),
//                LogicRunFrameState.valueOf(collection[stateKey] as String),
                dependenciesValue.map { ofCollection(it) }
            )
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            locationKey to objectLocation.asString(),
            executionKey to executionId.value,
//            stateKey to state.name,
            dependenciesKey to dependencies.map { it.toCollection() }
        )
    }
}