package tech.kzen.auto.common.paradigm.task

import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess


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