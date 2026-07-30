package tech.kzen.auto.server.api.handler

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.exec.RunEngineLogicTrace
import tech.kzen.auto.server.service.impl.LogicStartAttempt
import tech.kzen.auto.server.service.impl.ServerLogicController
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.engine.StepMode
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse
import tech.kzen.lib.common.exec.logic.run.model.LogicStatus
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.store.DirectGraphStore


class LogicHandler(
    private val serverLogicController: ServerLogicController,
    private val runEngineLogicTrace: RunEngineLogicTrace,
    private val graphStore: DirectGraphStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    // Typed LogicStatus for both transports: GET /logic/status serializes it via respondJson, and the
    // /logic/events SSE route encodes it with the same serverJson (byte-identical, so pushed and polled
    // statuses parse through one client path). status() is @Synchronized — build the object here, then
    // encode OUTSIDE the monitor (the SSE route does exactly that).
    fun logicStatus(): LogicStatus {
        return serverLogicController.status()
    }


    // Subscribe to "the logic status may have changed". See ServerLogicController.observeStatus for the
    // contract — in particular, the listener runs on an engine thread on the hot path and must only hand off.
    fun observeLogicStatus(listener: () -> Unit): AutoCloseable {
        return serverLogicController.observeStatus(listener)
    }


    fun logicStart(parameters: Parameters, paused: Boolean): LogicStartAttempt {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val pauseOnError: Boolean = parameters
            .getAll(CommonRestApi.paramPauseOnError)
            ?.singleOrNull()
            ?.toBoolean()
            ?: false

        // The mode of the first step of a paused (stepping) start — Into (plain "Start Stepping") unless the
        // client asks to start stepping OVER (run a sub-Logic entered on the first boundary to completion).
        val stepMode: StepMode = parameters
            .getAll(CommonRestApi.paramStepMode)
            ?.singleOrNull()
            ?.let { StepMode.valueOf(it) }
            ?: StepMode.Into

        val graphDefinitionAttempt = runBlocking {
            graphStore.graphDefinition()
        }

        val startAttempt = runBlocking {
            serverLogicController.startAttempt(objectLocation, graphDefinitionAttempt, pauseOnError)
        }
        if (startAttempt !is LogicStartAttempt.Started) {
            return startAttempt
        }
        val logicRunId = startAttempt.runId

        // Start-time breakpoints ride the start request and are set before the drive below launches the
        // engine — race-free (a follow-up PUT after startRun could miss the earliest steps).
        val breakpoints: List<ObjectLocation> = parameters.getParamList(
            CommonRestApi.paramBreakpoint, ObjectLocation::parse)
        if (breakpoints.isNotEmpty()) {
            serverLogicController.setBreakpoints(logicRunId, breakpoints)
        }

        val response = runBlocking {
            if (paused) {
                // Atomic launch-park-then-first-step (see ServerLogicController.startStep) — NOT a separate
                // pause() + step(), which races on the run flags ("Can't step, already running").
                serverLogicController.startStep(logicRunId, stepMode)
            }
            else {
                serverLogicController.continueOrStart(logicRunId, graphDefinitionAttempt)
            }
        }

        if (response != LogicRunResponse.Submitted) {
            return LogicStartAttempt.Failed("The run was not submitted: $response")
        }

        return startAttempt
    }


    // Hash-addressed screenshot blob. Returns null (→ 404) for a missing param, a non-retained run, or an
    // unknown hash — the client thumbnail then falls back to blank, same as any cleared trace.
    fun logicTraceBinary(parameters: Parameters): ByteArray? {
        val runIdValue = parameters[CommonRestApi.paramRunId]
            ?: return null
        val hash = parameters[CommonRestApi.paramContentHash]
            ?: return null
        return runEngineLogicTrace.lookupBinary(LogicRunId(runIdValue), hash)
    }


    fun logicRequest(parameters: Parameters): ExecutionResult {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val executionId: LogicExecutionId = parameters.getParam(CommonRestApi.paramExecutionId) {
            value -> LogicExecutionId(value)
        }

        val params = mutableMapOf<String, List<String>>()
        for (e in parameters.entries()) {
            if (e.key == CommonRestApi.paramRunId ||
                e.key == CommonRestApi.paramExecutionId) {
                continue
            }
            params[e.key] = e.value
        }

        val request = ExecutionRequest(RequestParams(params), null)

        val result: ExecutionResult = runBlocking {
            serverLogicController.request(
                runId,
                executionId,
                request)
        }

        return result
    }


    fun logicCancel(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.cancel(runId)
        }

        return response.name
    }


    fun logicPause(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.pause(runId)
        }

        return response.name
    }


    fun logicContinueRun(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.continueOrStart(runId)
        }

        return response.name
    }


    fun logicSetBreakpoints(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val breakpoints: List<ObjectLocation> = parameters.getParamList(
            CommonRestApi.paramBreakpoint, ObjectLocation::parse)

        val response = serverLogicController.setBreakpoints(runId, breakpoints)

        return response.name
    }


    fun logicSetPauseOnError(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val pauseOnError: Boolean = parameters
            .getAll(CommonRestApi.paramPauseOnError)
            ?.singleOrNull()
            ?.toBoolean()
            ?: false

        val response = runBlocking {
            serverLogicController.setPauseOnError(runId, pauseOnError)
        }

        return response.name
    }


    fun logicContinueStep(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.step(runId)
        }

        return response.name
    }


    fun logicStepOver(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.stepOver(runId)
        }

        return response.name
    }


    fun logicStepOut(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.stepOut(runId)
        }

        return response.name
    }


    fun logicMoveTo(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)
        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)
        val target = ObjectLocation(documentPath, objectPath)

        // The frame to reposition — the run root when absent, so a caller that only ever moves within the
        // root document doesn't have to name it.
        val executionId: LogicExecutionId? = parameters.getParamOrNull(CommonRestApi.paramExecutionId) {
            value -> LogicExecutionId(value)
        }

        val response = runBlocking {
            serverLogicController.moveTo(runId, target, executionId = executionId)
        }

        return response.name
    }
}
