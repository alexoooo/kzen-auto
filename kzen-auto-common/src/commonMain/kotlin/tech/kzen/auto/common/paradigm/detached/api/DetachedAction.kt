package tech.kzen.auto.common.paradigm.detached.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest


interface DetachedAction {
    suspend fun execute(
            request: DetachedRequest
    ): ExecutionResult
}