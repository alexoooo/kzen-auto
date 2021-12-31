package tech.kzen.auto.common.paradigm.common.v1

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunResponse
import tech.kzen.auto.common.paradigm.common.v1.model.LogicStatus
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.locate.ObjectLocation


interface LogicController {
    fun status(): LogicStatus


    suspend fun start(
        root: ObjectLocation,
//        request: ExecutionRequest,
        graphDefinitionSnapshot: GraphDefinitionAttempt? = null
    ): LogicRunId?


    fun request(
        runId: LogicRunId,
        executionId: LogicExecutionId,
        request: ExecutionRequest
    ): ExecutionResult


    fun cancel(runId: LogicRunId): LogicRunResponse
//    fun pause(runId: LogicRunId): LogicRunResponse


    fun continueOrStart(
        runId: LogicRunId,
        graphDefinitionSnapshot: GraphDefinitionAttempt? = null
    ): LogicRunResponse
//    fun step(runId: LogicRunId): LogicRunResponse
}