package tech.kzen.auto.common.paradigm.detached.service

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.lib.common.model.location.ObjectLocation


interface DetachedExecutor {
    suspend fun execute(
            actionLocation: ObjectLocation,
            request: ExecutionRequest
    ): ExecutionResult
}
