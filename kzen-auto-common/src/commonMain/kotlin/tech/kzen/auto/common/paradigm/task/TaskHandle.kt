package tech.kzen.auto.common.paradigm.task

import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess


interface TaskHandle {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @param result used if non-null, otherwise the partial result is required
     */
    fun complete(result: ExecutionResult? = null)

    /**
     * @param result used if non-null, otherwise the partial result is required
     */
    fun completeAsCancelled(result: ExecutionResult? = null)

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