package tech.kzen.auto.server.service

import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.service.ExecutionInitializer


object EmptyExecutionInitializer: ExecutionInitializer {
    override suspend fun initialExecutionModel(): ExecutionModel {
        return ExecutionModel(mutableListOf())
    }
}
