package tech.kzen.auto.common.paradigm.detached.api

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult


interface DetachedAction {
    suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult
}