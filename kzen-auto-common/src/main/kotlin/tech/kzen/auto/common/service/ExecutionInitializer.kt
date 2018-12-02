package tech.kzen.auto.common.service

import tech.kzen.auto.common.exec.ExecutionModel


interface ExecutionInitializer {
    suspend fun initialExecutionModel(): ExecutionModel
}