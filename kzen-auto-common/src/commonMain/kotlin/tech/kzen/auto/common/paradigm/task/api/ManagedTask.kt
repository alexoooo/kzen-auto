package tech.kzen.auto.common.paradigm.task.api

import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest


interface ManagedTask {
    suspend fun start(
        request: DetachedRequest,
        handle: TaskHandle
    )
}