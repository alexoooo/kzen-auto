package tech.kzen.auto.common.paradigm.imperative.service

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.lib.common.model.document.DocumentPath


interface ExecutionInitializer {
    suspend fun initialExecutionModel(host: DocumentPath): ExecutionModel
}