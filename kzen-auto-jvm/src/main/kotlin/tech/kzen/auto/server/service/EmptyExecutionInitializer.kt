package tech.kzen.auto.server.service

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionInitializer
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.platform.collect.persistentListOf


object EmptyExecutionInitializer: ExecutionInitializer {
    override suspend fun initialExecutionModel(
            host: DocumentPath
    ): ExecutionModel {
        return ExecutionModel(persistentListOf())
    }
}
