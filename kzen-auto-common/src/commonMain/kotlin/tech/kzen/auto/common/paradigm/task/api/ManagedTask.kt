package tech.kzen.auto.common.paradigm.task.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest


interface ManagedTask {
    suspend fun start(
        request: ExecutionRequest,
        handle: TaskHandle
    ): TaskRun?
}