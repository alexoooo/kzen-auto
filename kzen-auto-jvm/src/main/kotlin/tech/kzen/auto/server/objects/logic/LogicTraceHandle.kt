package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath


interface LogicTraceHandle {
    fun set(
        logicTracePath: LogicTracePath,
        executionValue: ExecutionValue)
}