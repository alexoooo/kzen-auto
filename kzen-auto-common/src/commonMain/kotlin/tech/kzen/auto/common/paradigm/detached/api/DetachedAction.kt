package tech.kzen.auto.common.paradigm.detached.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult


interface DetachedAction {
    suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult
}