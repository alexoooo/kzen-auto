package tech.kzen.auto.common.paradigm.detached

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult


interface DetachedAction {
    suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult
}