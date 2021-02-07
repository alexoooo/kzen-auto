package tech.kzen.auto.common.paradigm.task.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess


interface TaskHandle {
    //-----------------------------------------------------------------------------------------------------------------
    fun completeWithPartialResult()
    fun complete(result: ExecutionResult)

    fun update(partialResult: ExecutionSuccess)
    fun update(updater: (ExecutionSuccess?) -> ExecutionSuccess)


    //-----------------------------------------------------------------------------------------------------------------
    fun cancelRequested(): Boolean

    fun completeCancelled()

    fun isTerminated(): Boolean

    //-----------------------------------------------------------------------------------------------------------------
//    fun processRequests(processor: (DetachedRequest) -> ExecutionResult)
}