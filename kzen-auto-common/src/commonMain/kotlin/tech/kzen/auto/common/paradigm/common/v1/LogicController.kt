package tech.kzen.auto.common.paradigm.common.v1

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunResponse
import tech.kzen.auto.common.paradigm.common.v1.model.LogicStatus
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation


interface LogicController {
    fun status(): LogicStatus


    fun start(
        root: ObjectLocation,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null
    ): LogicRunId?


    fun request(
        runId: LogicRunId,
        executionId: LogicExecutionId,
        request: ExecutionRequest
    ): ExecutionResult


    fun cancel(runId: LogicRunId): LogicRunResponse


    fun pause(runId: LogicRunId): LogicRunResponse


    fun continueOrStart(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null
    ): LogicRunResponse


    fun step(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null
    ): LogicRunResponse


//    suspend fun startStep(
//        root: ObjectLocation,
//        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null
//    ): LogicRunId?
}