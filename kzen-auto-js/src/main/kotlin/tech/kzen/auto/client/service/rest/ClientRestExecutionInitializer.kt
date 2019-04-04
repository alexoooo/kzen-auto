package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionInitializer
import tech.kzen.lib.common.model.document.DocumentPath


class ClientRestExecutionInitializer(
        private val clientRestApi: ClientRestApi
): ExecutionInitializer {
    override suspend fun initialExecutionModel(
            host: DocumentPath
    ): ExecutionModel {
        return clientRestApi.executionModel(host)
    }
}