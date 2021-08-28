package tech.kzen.auto.server.service.v1

import kotlinx.datetime.Clock
import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.v1.LogicController
import tech.kzen.auto.common.paradigm.common.v1.model.*
import tech.kzen.auto.server.objects.logic.LogicTraceStore
import tech.kzen.auto.server.service.v1.model.LogicResultFailed
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.auto.server.service.v1.model.context.LogicFrame
import tech.kzen.auto.server.service.v1.model.context.MutableLogicControl
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


class ServerLogicController(
    private val graphStore: LocalGraphStore,
    private val graphCreator: GraphCreator
): LogicController {
    //-----------------------------------------------------------------------------------------------------------------
    private data class LogicState(
        val runId: LogicRunId,
        val frame: LogicFrame,
        val objectStableMapper: ObjectStableMapper
    ) {
        var cancelRequested: Boolean = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var stateOrNull: LogicState? = null


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override fun status(): LogicStatus {
        val runInfo = stateOrNull?.let {
            val runState =
                if (it.cancelRequested) {
                    LogicRunState.Cancelling
                }
                else {
                    LogicRunState.Running
                }

            val frame = it.frame.toInfo(it.objectStableMapper)

            LogicRunInfo(
                it.runId,
                frame,
                runState
            )
        }

        val time = Clock.System.now()
        return LogicStatus(time, runInfo)
    }


    @Synchronized
    override suspend fun start(
        root: ObjectLocation,
//        request: ExecutionRequest
    ): LogicRunId? {
        val state = stateOrNull
        if (state != null) {
            return null
        }

        val runId = LogicRunId(Clock.System.now().toString())
        val executionId = LogicExecutionId(runId.value)

        val objectStableMapper = ObjectStableMapper()

        val successfulGraphDefinition = graphStore
            .graphDefinition()
            .successful()

        val rootGraphInstance = graphCreator.createGraph(
            successfulGraphDefinition.filterTransitive(root))

        val rootInstance = rootGraphInstance.objectInstances[root]?.reference
            ?: return null

        val logicHandle: LogicHandle = object : LogicHandle {
            override fun start(originalObjectLocation: ObjectLocation): LogicHandle.Execution {
                TODO("Not yet implemented")
//                val dependencyGraphInstance = graphCreator.createGraph(
//                    successfulGraphDefinition.filterTransitive(originalObjectLocation))
//
//                val dependencyInstance = rootGraphInstance.objectInstances[root]?.reference as? Logic
//                    ?: throw IllegalArgumentException("Dependency logic not found: $originalObjectLocation")
//
//                val dependencyExecution = dependencyInstance.execute(this)
//
//                return object : LogicHandle.Execution {
//                    override fun next(arguments: TupleValue): LogicResult {
//                        return dependencyExecution.next(arguments)
//                    }
//
//                    override fun run(): LogicResult {
//                        dependencyExecution.run()
//                    }
//                }
            }
        }

        val logicTraceHandle = LogicTraceStore.handle(runId, executionId)

        val logic = rootInstance as Logic
        val execution = logic.execute(logicHandle, logicTraceHandle)

        stateOrNull = LogicState(
            runId,
            LogicFrame(
                objectStableMapper.objectStableId(root),
                executionId,
                execution,
                LogicRunFrameState.Ready,
                listOf(),
                MutableLogicControl()
            ),
            objectStableMapper
        )

        return runId
    }


    @Synchronized
    override fun request(
        runId: LogicRunId,
        executionId: LogicExecutionId,
        request: ExecutionRequest
    ): ExecutionResult {
        val state = stateOrNull
            ?: return ExecutionResult.failure("Not running")

        if (state.runId != runId) {
            return ExecutionResult.failure(
                "Expected runId '$runId' but was '${state.runId}'")
        }

        val frame = state.frame.find(executionId)
            ?: return ExecutionResult.failure(
                "Execution '$executionId' not found in run '$runId'")

        val resultPromise = frame.control.addRequest(request)

        return resultPromise.get()
    }


    @Synchronized
    override fun cancel(runId: LogicRunId): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.Rejected
        }

        state.frame.control.commandCancel()

        return LogicRunResponse.Submitted
    }


    @Synchronized
    override fun run(runId: LogicRunId): LogicRunResponse {
        val state = stateOrNull
            ?: return LogicRunResponse.NotFound

        if (state.runId != runId) {
            return LogicRunResponse.Rejected
        }

        val ready = state.frame.execution.next(TupleValue.empty)
        if (! ready) {
            return LogicRunResponse.Aborted
        }

        Thread {
            val result = state.frame.execution.run(state.frame.control)

            if (result.isTerminal()) {
                state.frame.execution.close(result is LogicResultFailed)
                clearState()
            }
        }.start()

        return LogicRunResponse.Submitted
    }


    @Synchronized
    private fun clearState() {
        stateOrNull = null
    }
}