package tech.kzen.auto.common.paradigm.task.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest


interface TaskHandle {
    //-----------------------------------------------------------------------------------------------------------------
    fun complete(result: ExecutionResult)

    fun update(partialResult: ExecutionSuccess)


    //-----------------------------------------------------------------------------------------------------------------
    fun cancelRequested(): Boolean

    fun completeCancelled()


    //-----------------------------------------------------------------------------------------------------------------
    fun processRequests(processor: (DetachedRequest) -> ExecutionResult)
}