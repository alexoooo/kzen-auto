package tech.kzen.auto.client.service

import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.service.ExecutionInitializer


class ClientRestExecutionInitializer(
        private val clientRestApi: ClientRestApi
): ExecutionInitializer {
    override suspend fun initialExecutionModel(): ExecutionModel {
        val encoding = clientRestApi.executionModel()
        return encoding.decode()
    }
}