package tech.kzen.auto.common.paradigm.task.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess


interface TaskHandle {
    //-----------------------------------------------------------------------------------------------------------------
    fun completeWithPartialResult()
    fun complete(result: ExecutionResult)

    fun update(partialResult: ExecutionSuccess)
    fun update(updater: (ExecutionSuccess?) -> ExecutionSuccess)


    fun terminalFailure(error: ExecutionFailure)


    //-----------------------------------------------------------------------------------------------------------------
    fun stopRequested(): Boolean

    fun isFailed(): Boolean

    fun isTerminated(): Boolean


    fun awaitTerminal()


    //-----------------------------------------------------------------------------------------------------------------
//    fun processRequests(processor: (DetachedRequest) -> ExecutionResult)
}