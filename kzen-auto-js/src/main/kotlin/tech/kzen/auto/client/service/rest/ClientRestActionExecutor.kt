package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeError
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
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

    override suspend fun execute(
            actionLocation: ObjectLocation,
            activeModel: ImperativeModel
    ): ImperativeResult {
        return try {
            restClient.performAction(actionLocation).executionResult
        }
        catch (e: Exception) {
//            println("#$%#$%#$ got exception: $e")
            ImperativeError(e.message ?: "Error")
        }
    }
}