package tech.kzen.auto.client.service

import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.codec.ExecutionResultResponse
import tech.kzen.auto.common.objects.service.ActionManager
import tech.kzen.auto.common.service.ActionExecutor
import tech.kzen.lib.common.api.model.ObjectLocation


class ClientRestActionExecutor(
        private val restClient: ClientRestApi
): ActionExecutor {
    override suspend fun actionManager(): ActionManager {
        TODO()
    }

    override suspend fun executeResult(actionLocation: ObjectLocation): ExecutionResult {
        TODO()
    }

    override suspend fun executeResponse(actionLocation: ObjectLocation): ExecutionResultResponse {
        return restClient.performAction(actionLocation)
    }
}