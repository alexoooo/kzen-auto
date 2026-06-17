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
import org.slf4j.LoggerFactory


@Reflect
class LogicTraceEndpoint(
    @Service private val logicTraceStore: LogicTraceStore
): DetachedAction {
    // TEMP DIAGNOSTIC (screenshots-missing-for-sub-script bug): logs how a document's run is
    // resolved (mostRecent) and what its trace lookup returns, so a poisoned-process reproduction
    // reveals which of the three server-side failure modes is in play. Remove once root-caused.
    companion object {
        private val logger = LoggerFactory.getLogger(LogicTraceEndpoint::class.java)
    }


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

                logger.info(
                    "[trace-diag] mostRecent({}) -> runId={} executionId={}",
                    objectLocation.asString(),
                    mostRecent?.logicRunId?.value,
                    mostRecent?.logicExecutionId?.value)

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

                logger.info(
                    "[trace-diag] lookup(runId={} executionId={} query={}) -> {}",
                    logicRunId.value,
                    logicExecutionId.value,
                    logicTraceQuery.asString(),
                    snapshot?.let { "${it.values.size} value(s): keys=${it.values.keys.map { p -> p.asString() }}" }
                        ?: "NULL (buffer not found / evicted)")

                if (snapshot == null) {
                    return ExecutionResult.failure(
                        "Logic Trace not found: $logicRunId / $logicExecutionId / $logicTraceQuery")
                }

                ExecutionSuccess.ofValue(ExecutionValue.of(
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
                val cleared = logicTraceStore.clear(objectLocation)

                ExecutionSuccess.ofValue(ExecutionValue.of(cleared))
            }

            else ->
                ExecutionResult.failure("Unknown logic trace action: '$action'")
        }
    }
}
