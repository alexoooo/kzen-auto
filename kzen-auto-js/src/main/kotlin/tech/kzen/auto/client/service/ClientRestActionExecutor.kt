package tech.kzen.auto.client.service

import tech.kzen.auto.common.service.ActionExecutor


class ClientRestActionExecutor(
        private val restClient: ClientRestApi
): ActionExecutor {
    override suspend fun execute(actionName: String) {
        restClient.performAction(actionName)
    }
}