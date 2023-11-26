package tech.kzen.auto.common.paradigm.task.api

import tech.kzen.lib.common.exec.ExecutionRequest


interface ManagedTask {
    suspend fun start(
        request: ExecutionRequest,
        handle: TaskHandle
    ): TaskRun?
}