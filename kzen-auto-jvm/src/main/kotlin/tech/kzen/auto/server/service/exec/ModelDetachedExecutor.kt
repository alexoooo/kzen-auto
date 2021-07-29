package tech.kzen.auto.server.service.exec

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.service.DetachedExecutor
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadAction
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadExecutor
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore


class ModelDetachedExecutor(
        private val graphStore: LocalGraphStore,
        private val graphCreator: GraphCreator
):
    DetachedExecutor, DetachedDownloadExecutor
{
    companion object {
        private val logger = LoggerFactory.getLogger(ModelDetachedExecutor::class.java)
    }


    override suspend fun execute(
            actionLocation: ObjectLocation,
            request: ExecutionRequest
    ): ExecutionResult {
        val graphDefinition = graphStore
                .graphDefinition()
                .transitiveSuccessful()
                .filterDefinitions(AutoConventions.serverAllowed)

        val objectGraph = graphCreator
                .createGraph(graphDefinition)

        val instance = objectGraph.objectInstances[actionLocation]?.reference
                ?: return ExecutionFailure("Not found: $actionLocation")

        val action = instance as? DetachedAction
                ?: return ExecutionFailure("Not DetachedAAction: $actionLocation - $instance")

        return try {
            action.execute(request)
        }
        catch (t: Throwable) {
            logger.warn("{} - {}", actionLocation, request, t)
            ExecutionFailure.ofException(t)
        }
    }


    override suspend fun executeDownload(
        actionLocation: ObjectLocation,
        request: ExecutionRequest
    ): ExecutionDownloadResult {
        val graphDefinition = graphStore
            .graphDefinition()
            .transitiveSuccessful()
            .filterDefinitions(AutoConventions.serverAllowed)

        val objectGraph = graphCreator
            .createGraph(graphDefinition)

        val instance = objectGraph.objectInstances[actionLocation]?.reference
            ?: error("Not found: $actionLocation")

        val action = instance as? DetachedDownloadAction
            ?: error("Not DetachedAAction: $actionLocation - $instance")

        return action.executeDownload(request)
    }
}