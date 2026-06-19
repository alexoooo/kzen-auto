package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore


@Reflect
class LogicTraceEndpoint(
    @Service private val logicTraceStore: LogicTraceStore
): DetachedAction {
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
                val mostRecent = logicTraceStore.mostRecent(objectLocation)

                ExecutionSuccess.ofValue(ExecutionValue.of(
                    mostRecent?.let { LogicConventions.runExecutionAsCollection(it) }
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
                val snapshot = logicTraceStore.lookup(runExecutionId, logicTraceQuery)
                    ?: return ExecutionResult.failure(
                        "Logic Trace not found: $logicRunId / $logicExecutionId / $logicTraceQuery")

                ExecutionSuccess.ofValue(ExecutionValue.of(
                    snapshot.asCollection()))
            }

            LogicConventions.actionLookupRun -> {
                val logicRunId = request.getSingle(CommonRestApi.paramRunId)?.let { LogicRunId(it) }
                    ?: return ExecutionResult.failure("Logic Run ID missing: '${CommonRestApi.paramRunId}'")

                val logicTraceQuery = request.getSingle(LogicConventions.paramQuery)?.let { LogicTraceQuery.parse(it) }
                    ?: return ExecutionResult.failure("Logic Trace Query missing")

                val snapshot = logicTraceStore.lookupRun(logicRunId, logicTraceQuery)
                    ?: return ExecutionResult.failure("Logic Trace not found for run: $logicRunId")

                ExecutionSuccess.ofValue(ExecutionValue.of(
                    snapshot.asCollection()))
            }

            LogicConventions.actionLookupRunHistory -> {
                val logicRunId = request.getSingle(CommonRestApi.paramRunId)?.let { LogicRunId(it) }
                    ?: return ExecutionResult.failure("Logic Run ID missing: '${CommonRestApi.paramRunId}'")

                val sinceSequence = request.getSingle(LogicConventions.paramSinceSequence)?.toLong()
                    ?: return ExecutionResult.failure("Since-sequence missing: '${LogicConventions.paramSinceSequence}'")

                val events = logicTraceStore.lookupRunHistory(logicRunId, sinceSequence)

                ExecutionSuccess.ofValue(ExecutionValue.of(
                    events.map { it.toCollection() }))
            }

            LogicConventions.actionTraced -> {
                // Every document with a retained trace (run roots + sub-logic roots), as documentPath
                // strings — the sidebar keys its "has trace" indicator by DocumentPath.
                val documentPaths = logicTraceStore.tracedLocations()
                    .map { it.documentPath.asString() }
                    .distinct()

                ExecutionSuccess.ofValue(ExecutionValue.of(documentPaths))
            }

            LogicConventions.actionReset -> {
                val documentPath: DocumentPath = request.getSingle(LogicConventions.paramSubDocumentPath)
                    ?.let { DocumentPath.parse(it) }
                    ?: return ExecutionResult.failure("Document path missing: '${LogicConventions.paramSubDocumentPath}'")

                val objectPath: ObjectPath = request.getSingle(LogicConventions.paramSubObjectPath)
                    ?.let { ObjectPath.parse(it) }
                    ?: return ExecutionResult.failure("Object path missing: '${LogicConventions.paramSubObjectPath}'")

                val objectLocation = ObjectLocation(documentPath, objectPath)
                val cleared = logicTraceStore.clear(objectLocation)

                ExecutionSuccess.ofValue(ExecutionValue.of(cleared))
            }

            LogicConventions.actionResetAll -> {
                // Global clear: the run controls are global, so Clear wipes every retained trace.
                logicTraceStore.clearAll()
                ExecutionSuccess.ofValue(ExecutionValue.of(true))
            }

            else ->
                ExecutionResult.failure("Unknown logic trace action: '$action'")
        }
    }
}
