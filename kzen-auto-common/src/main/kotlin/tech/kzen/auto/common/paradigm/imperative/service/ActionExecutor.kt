package tech.kzen.auto.common.paradigm.imperative.service

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.lib.common.api.model.ObjectLocation


interface ActionExecutor {
//    suspend fun executeResult(actionLocation: ObjectLocation): ExecutionResult
//    suspend fun executeResponse(actionLocation: ObjectLocation): ExecutionResultResponse
    suspend fun execute(actionLocation: ObjectLocation): ExecutionResult

//    suspend fun actionManager(): ActionManager
}