package tech.kzen.auto.common.paradigm.task.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess


interface TaskHandle {
    fun updateAsync(activeState: Any)

    fun complete(result: ExecutionResult)

    fun completeCancelled()

    fun update(partialResult: ExecutionSuccess)

    fun cancelRequested(): Boolean
}