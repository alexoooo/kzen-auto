package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.v1.model.LogicConventions
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.common.v1.trace.LogicTrace
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceQuery
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


@Reflect
object LogicTraceStore:
    LogicTrace,
    DetachedAction
{
    //-----------------------------------------------------------------------------------------------------------------
    private data class RunExecution(
        val runExecutionId: LogicRunExecutionId
    )


    private class TraceBuffer {
        val values = ConcurrentHashMap<LogicTracePath, ExecutionValue>()
        val callbacks = CopyOnWriteArrayList<(LogicTraceQuery) -> Unit>()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val history = ConcurrentHashMap<RunExecution, TraceBuffer>()


    //-----------------------------------------------------------------------------------------------------------------
    fun handle(
        runExecutionId: LogicRunExecutionId
    ): LogicTraceHandle {
        val buffer = getOrCreateBuffer(runExecutionId)

        return object : LogicTraceHandle {
            override fun register(callback: (LogicTraceQuery) -> Unit): AutoCloseable {
                buffer.callbacks.add(callback)
                return AutoCloseable {
                    buffer.callbacks.remove(callback)
                }
            }

            override fun set(logicTracePath: LogicTracePath, executionValue: ExecutionValue) {
                buffer.values[logicTracePath] = executionValue
            }
        }
    }


    private fun getOrCreateBuffer(
        runExecutionId: LogicRunExecutionId
    ): TraceBuffer {
        val runExecution = RunExecution(runExecutionId)
        return history.getOrPut(runExecution) { TraceBuffer() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val logicRunId = request.getSingle(LogicConventions.runIdKey)?.let { LogicRunId(it) }
            ?: return ExecutionResult.failure("Logic Run ID missing: '${LogicConventions.runIdKey}'")

        val logicExecutionId = request.getSingle(LogicConventions.executionIdKey)?.let { LogicExecutionId(it) }
            ?: return ExecutionResult.failure("Logic Execution ID missing: '${LogicConventions.executionIdKey}'")

        val logicTraceQuery = request.getSingle(LogicConventions.queryKey)?.let { LogicTraceQuery.parse(it) }
            ?: return ExecutionResult.failure("Logic Trade Query missing")

        val runExecutionId = LogicRunExecutionId(logicRunId, logicExecutionId)
        val snapshot = lookup(runExecutionId, logicTraceQuery)
            ?: return ExecutionResult.failure(
                "Logic Trace not found: $logicRunId / $logicExecutionId / $logicTraceQuery")

        return ExecutionSuccess.ofValue(ExecutionValue.of(snapshot.asCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun lookup(
        logicRunExecutionId: LogicRunExecutionId,
        logicTraceQuery: LogicTraceQuery
    ):
        LogicTraceSnapshot?
    {
        val runExecution = RunExecution(logicRunExecutionId)

        val buffer = history[runExecution]
            ?: return null

        buffer.callbacks.forEach { it(logicTraceQuery) }

        val matchingValues = buffer
            .values
            .filter { logicTraceQuery.match(it.key) }

        return LogicTraceSnapshot(matchingValues)
    }
}