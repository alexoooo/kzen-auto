package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceQuery


interface LogicTraceHandle {
    fun register(callback: (LogicTraceQuery) -> Unit): AutoCloseable


    fun set(
        logicTracePath: LogicTracePath,
        executionValue: ExecutionValue)


    fun clearAll(prefix: LogicTracePath)
}