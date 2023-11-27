package tech.kzen.auto.common.paradigm.detached

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.model.location.ObjectLocation


interface DetachedExecutor {
    suspend fun execute(
        actionLocation: ObjectLocation,
        request: ExecutionRequest
    ): ExecutionResult
}
