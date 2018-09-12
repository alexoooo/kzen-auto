package tech.kzen.auto.client.service

import tech.kzen.auto.common.service.ActionExecutor


class RestActionExecutor(
        private val restClient: RestClient
): ActionExecutor {
    override suspend fun execute(actionName: String) {
        restClient.performAction(actionName)
    }
}