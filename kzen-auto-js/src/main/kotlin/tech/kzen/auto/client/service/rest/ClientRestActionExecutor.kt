package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.service.ActionExecutor
import tech.kzen.lib.common.api.model.ObjectLocation


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
        return restClient.performAction(actionLocation).executionResult
    }
}