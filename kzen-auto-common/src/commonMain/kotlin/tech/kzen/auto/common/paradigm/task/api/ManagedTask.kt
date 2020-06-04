package tech.kzen.auto.common.paradigm.task.api

import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest


interface ManagedTask {
    suspend fun start(request: DetachedRequest, handle: TaskHandle)

//    /**
//     * @return true if task was running and stopped as a result of the call
//     */
//    suspend fun cancel(): Boolean
}