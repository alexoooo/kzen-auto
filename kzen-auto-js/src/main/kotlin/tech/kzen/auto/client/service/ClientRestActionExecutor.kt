package tech.kzen.auto.client.service

import tech.kzen.auto.common.service.ActionExecutor
import tech.kzen.lib.common.api.model.ObjectLocation


class ClientRestActionExecutor(
        private val restClient: ClientRestApi
): ActionExecutor {
    override suspend fun execute(actionLocation: ObjectLocation) {
        restClient.performAction(actionLocation)
    }
}