package tech.kzen.auto.common.paradigm.common.v1.trace.model

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue

// {LogicTracePath -> value}?
data class LogicTraceSnapshot(
    val values: Map<LogicTracePath, ExecutionValue>
) {
    companion object {
        fun ofCollection(
            collection: Map<String, Map<String, Any>>
        ): LogicTraceSnapshot {
            val values = collection
                .map { LogicTracePath.parse(it.key) to ExecutionValue.fromJsonCollection(it.value) }
                .toMap()

            return LogicTraceSnapshot(values)
        }
    }


    fun asCollection(): Map<String, Map<String, Any>> {
        return values
            .map { it.key.asString() to it.value.toJsonCollection() }
            .toMap()
    }


    fun filter(startsWith: LogicTracePath): LogicTraceSnapshot {
        val filtered = values.filterKeys { i -> i.startsWith(startsWith) }
        return LogicTraceSnapshot(filtered)
    }
}