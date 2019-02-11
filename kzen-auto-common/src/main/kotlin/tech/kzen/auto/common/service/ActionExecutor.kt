package tech.kzen.auto.common.service

import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.codec.ExecutionResultResponse
import tech.kzen.auto.common.objects.service.ActionManager
import tech.kzen.lib.common.api.model.ObjectLocation


interface ActionExecutor {
    suspend fun executeResult(actionLocation: ObjectLocation): ExecutionResult
    suspend fun executeResponse(actionLocation: ObjectLocation): ExecutionResultResponse

    suspend fun actionManager(): ActionManager
}