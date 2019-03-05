package tech.kzen.auto.server.service

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionInitializer


object EmptyExecutionInitializer: ExecutionInitializer {
    override suspend fun initialExecutionModel(): ExecutionModel {
        return ExecutionModel(mutableListOf())
    }
}
