package tech.kzen.auto.common.paradigm.imperative.service

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel


interface ExecutionInitializer {
    suspend fun initialExecutionModel(): ExecutionModel
}