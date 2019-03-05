package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionInitializer


class ClientRestExecutionInitializer(
        private val clientRestApi: ClientRestApi
): ExecutionInitializer {
    override suspend fun initialExecutionModel(): ExecutionModel {
        return clientRestApi.executionModel()
    }
}