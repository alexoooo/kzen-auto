package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionError
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.service.ActionExecutor
import tech.kzen.lib.common.model.locate.ObjectLocation


class ClientRestActionExecutor(
        private val restClient: ClientRestApi
): ActionExecutor {
//    override suspend fun actionManager(): ActionManager {
//        TODO()
//    }
//
//    override suspend fun executeResult(actionLocation: ObjectLocation): ExecutionResult {
//        TODO()
//    }

    override suspend fun execute(actionLocation: ObjectLocation): ExecutionResult {
        return try {
            restClient.performAction(actionLocation).executionResult
        }
        catch (e: Exception) {
//            println("#$%#$%#$ got exception: $e")
            ExecutionError(e.message ?: "Error")
        }
    }
}