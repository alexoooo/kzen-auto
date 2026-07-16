package tech.kzen.auto.client.objects.document.job

import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * Per-Worker live state for the interactive Job view, projected from the run's retained engine (the successor
 * to the retired `JobExecution`). Mirrors the Flow store's mostRecent -> lookupRun query pair, reading from the
 * one snapshot, per Worker: its live PROGRESS (the `progress` child path, written by each Worker via
 * `JobControl.publishProgress`) and its terminal OUTCOME (the node-outcome path — see [WorkerOutcome] /
 * [LogicTracePath.nodeOutcome], the source of the Job UI outcome chip). The progress payload is an opaque
 * per-Worker map; this store does not interpret its keys — each Worker's display parses its own out of
 * [JobWorkerProgress.progressMap]. (The legacy bare stable-id STATUS path is still read for back-compat but is
 * unwritten on the current engine.)
 */
class JobProgressStore(
    private val restClient: ClientRestApi,
    private val objectStableMapper: ObjectStableMapper
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun fetchWorkerProgress(
        mainLocation: ObjectLocation,
        workerLocations: Collection<ObjectLocation>
    ): Map<ObjectLocation, JobWorkerProgress> {
        val logicRunExecutionId = mostRecent(mainLocation)
            ?: return mapOf()

        val snapshot = lookupRun(logicRunExecutionId.logicRunId)
            ?: return mapOf()

        val builder = mutableMapOf<ObjectLocation, JobWorkerProgress>()
        for (workerLocation in workerLocations) {
            val stableId = objectStableMapper.objectStableId(workerLocation)
            val basePath = LogicTracePath.ofObjectStableId(stableId)

            val status = snapshot.values[basePath]?.value?.get()?.toString()

            val progressRaw = snapshot
                .values[JobConventions.workerProgressPath(stableId)]
                ?.value
                ?.get()

            // The Worker node's terminal outcome (flavour-neutral projection; survives post-run via the retained
            // engine) — the source of the outcome chip.
            val outcomeRaw = snapshot
                .values[LogicTracePath.nodeOutcome(stableId)]
                ?.value
                ?.get()

            if (status == null && progressRaw == null && outcomeRaw == null) {
                continue
            }
            builder[workerLocation] = JobWorkerProgress.ofProgressMap(status, progressRaw, outcomeRaw)
        }
        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun mostRecent(mainLocation: ObjectLocation): LogicRunExecutionId? {
        val result = restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionMostRecent,
            LogicConventions.paramSubDocumentPath to mainLocation.documentPath.asString(),
            LogicConventions.paramSubObjectPath to mainLocation.objectPath.asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultCollection = result.value.get() as Map<String, String>?
                resultCollection?.let { LogicConventions.runExecutionFromCollection(it) }
            }

            is ExecutionFailure ->
                null
        }
    }


    private suspend fun lookupRun(logicRunId: LogicRunId): LogicTraceSnapshot? {
        val result = restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionLookupRun,
            CommonRestApi.paramRunId to logicRunId.value,
            LogicConventions.paramQuery to LogicTraceQuery(LogicTracePath.root).asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Map<String, Any>>
                LogicTraceSnapshot.ofCollection(resultValue)
            }

            is ExecutionFailure ->
                null
        }
    }
}
