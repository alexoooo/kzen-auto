package tech.kzen.auto.common.paradigm.detached.service

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.lib.common.model.locate.ObjectLocation


interface DetachedExecutor {
    suspend fun execute(
            actionLocation: ObjectLocation,
            request: DetachedRequest
    ): ExecutionResult
}
