package tech.kzen.auto.client.objects.document.flow

import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.platform.collect.toPersistentMap


/**
 * Builds a [VisualFlowModel] for a Flow document by reading the logic trace store, replacing the
 * retired `/dataflow/model` repository. The server records each vertex's [VisualVertexModel] (as JSON)
 * to the trace path [LogicTracePath.ofObjectStableId]; this mirrors
 * [tech.kzen.auto.client.objects.document.script.progress.ScriptProgressStore]'s mostRecent ->
 * lookupRun query pair, then resolves each vertex location's stable-id trace path from the snapshot.
 */
class FlowProgressStore(
    private val restClient: ClientRestApi,
    private val objectStableMapper: ObjectStableMapper
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun fetchVisualModel(
        mainLocation: ObjectLocation,
        vertexLocations: Collection<ObjectLocation>
    ): VisualFlowModel {
        val logicRunExecutionId = mostRecent(mainLocation)
            ?: return VisualFlowModel.empty

        val snapshot = lookupRun(logicRunExecutionId.logicRunId)
            ?: return VisualFlowModel.empty

        val builder = mutableMapOf<ObjectLocation, VisualVertexModel>()
        for (vertexLocation in vertexLocations) {
            val stableId = objectStableMapper.objectStableId(vertexLocation)
            val tracePath = LogicTracePath.ofObjectStableId(stableId)
            val entry = snapshot.values[tracePath]
            builder[vertexLocation] = entry
                ?.let { deserialize(it.value) }
                ?: VisualVertexModel.empty
        }
        return VisualFlowModel(builder.toPersistentMap())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun deserialize(executionValue: ExecutionValue): VisualVertexModel? {
        @Suppress("UNCHECKED_CAST")
        val collection = executionValue.get() as? Map<String, Any?>
            ?: return null
        return VisualVertexModel.fromCollection(collection)
    }


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
