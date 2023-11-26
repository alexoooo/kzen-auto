package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.common.v1.model.LogicConventions
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.common.v1.trace.LogicTrace
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceQuery
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
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
    private val objectLocationHistory = ConcurrentHashMap<ObjectLocation, LogicRunExecutionId>()


    //-----------------------------------------------------------------------------------------------------------------
    fun handle(
        runExecutionId: LogicRunExecutionId,
        objectLocation: ObjectLocation
    ): LogicTraceHandle {
        val buffer = getOrCreateBuffer(runExecutionId, objectLocation)

        return object: LogicTraceHandle {
            override fun register(callback: (LogicTraceQuery) -> Unit): AutoCloseable {
                buffer.callbacks.add(callback)
                return AutoCloseable {
                    buffer.callbacks.remove(callback)
                }
            }

            override fun set(logicTracePath: LogicTracePath, executionValue: ExecutionValue) {
                buffer.values[logicTracePath] = executionValue
            }

            override fun clearAll(prefix: LogicTracePath) {
                val pathsToClear = buffer.values.keys.filter { it.startsWith(prefix) }
                for (pathToClear in pathsToClear) {
                    buffer.values.remove(pathToClear)
                }
            }
        }
    }


    private fun getOrCreateBuffer(
        runExecutionId: LogicRunExecutionId,
        objectLocation: ObjectLocation
    ): TraceBuffer {
        objectLocationHistory[objectLocation] = runExecutionId

        val runExecution = RunExecution(runExecutionId)
        return history.getOrPut(runExecution) { TraceBuffer() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val action = request.getSingle(CommonRestApi.paramAction)
            ?: return ExecutionResult.failure("Action missing: '${CommonRestApi.paramAction}'")

        return when (action) {
            LogicConventions.actionMostRecent -> {
                val documentPath: DocumentPath = request.getSingle(LogicConventions.paramSubDocumentPath)
                    ?.let { DocumentPath.parse(it) }
                    ?: return ExecutionResult.failure("Document path missing: '${LogicConventions.paramSubDocumentPath}'")

                val objectPath: ObjectPath = request.getSingle(LogicConventions.paramSubObjectPath)
                    ?.let { ObjectPath.parse(it) }
                    ?: return ExecutionResult.failure("Object path missing: '${LogicConventions.paramSubObjectPath}'")

                val objectLocation = ObjectLocation(documentPath, objectPath)
                val mostRecent = mostRecent(objectLocation)

                ExecutionSuccess.ofValue(ExecutionValue.of(
                    mostRecent?.asCollection()
                ))
            }

            LogicConventions.actionLookup -> {
                val logicRunId = request.getSingle(CommonRestApi.paramRunId)?.let { LogicRunId(it) }
                    ?: return ExecutionResult.failure("Logic Run ID missing: '${CommonRestApi.paramRunId}'")

                val logicExecutionId = request.getSingle(CommonRestApi.paramExecutionId)?.let { LogicExecutionId(it) }
                    ?: return ExecutionResult.failure("Logic Execution ID missing: '${CommonRestApi.paramExecutionId}'")

                val logicTraceQuery = request.getSingle(LogicConventions.paramQuery)?.let { LogicTraceQuery.parse(it) }
                    ?: return ExecutionResult.failure("Logic Trade Query missing")

                val runExecutionId = LogicRunExecutionId(logicRunId, logicExecutionId)
                val snapshot = lookup(runExecutionId, logicTraceQuery)
                    ?: return ExecutionResult.failure(
                        "Logic Trace not found: $logicRunId / $logicExecutionId / $logicTraceQuery")

                return ExecutionSuccess.ofValue(ExecutionValue.of(
                    snapshot.asCollection()))
            }

            LogicConventions.actionReset -> {
                val documentPath: DocumentPath = request.getSingle(LogicConventions.paramSubDocumentPath)
                    ?.let { DocumentPath.parse(it) }
                    ?: return ExecutionResult.failure("Document path missing: '${LogicConventions.paramSubDocumentPath}'")

                val objectPath: ObjectPath = request.getSingle(LogicConventions.paramSubObjectPath)
                    ?.let { ObjectPath.parse(it) }
                    ?: return ExecutionResult.failure("Object path missing: '${LogicConventions.paramSubObjectPath}'")

                val objectLocation = ObjectLocation(documentPath, objectPath)
                val cleared = clear(objectLocation)

                ExecutionSuccess.ofValue(ExecutionValue.of(cleared))
            }
            else ->
                ExecutionResult.failure("Unknown logic trace action: '$action'")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun mostRecent(objectLocation: ObjectLocation): LogicRunExecutionId? {
        return objectLocationHistory[objectLocation]
    }


    override fun clear(objectLocation: ObjectLocation): Boolean {
        return objectLocationHistory.remove(objectLocation) != null
    }


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