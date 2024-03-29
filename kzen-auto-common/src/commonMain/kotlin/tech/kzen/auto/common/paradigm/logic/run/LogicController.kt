package tech.kzen.auto.common.paradigm.logic.run

import tech.kzen.auto.common.paradigm.logic.run.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunId
import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunResponse
import tech.kzen.auto.common.paradigm.logic.run.model.LogicStatus
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
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