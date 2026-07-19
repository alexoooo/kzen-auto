package tech.kzen.auto.server.service.exec

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.common.paradigm.detached.DetachedExecutor
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadAction
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadExecutor
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.LocalGraphStore


class ModelDetachedExecutor(
    private val graphStore: LocalGraphStore,
    private val graphInstanceCache: GraphInstanceCache
):
    DetachedExecutor, DetachedDownloadExecutor
{
    companion object {
        private val logger = LoggerFactory.getLogger(ModelDetachedExecutor::class.java)
    }


    // The action is instantiated from its own transitive definition closure (of the serverAllowed
    // subset - the policy filter comes first) and reused across calls while its notation is unchanged.
    private suspend fun actionInstance(actionLocation: ObjectLocation): Any? {
        val serverDefinition = graphStore
            .graphDefinition()
            .transitiveSuccessful
            .filterDefinitions(AutoConventions.serverAllowed)

        return graphInstanceCache
            .objectInstance(serverDefinition, actionLocation)
            ?.reference
    }


    override suspend fun execute(
        actionLocation: ObjectLocation,
        request: ExecutionRequest
    ): ExecutionResult {
        val instance = actionInstance(actionLocation)
            ?: return ExecutionFailure("Not found: $actionLocation")

        val action = instance as? DetachedAction
            ?: return ExecutionFailure("Not DetachedAction: $actionLocation - $instance")

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
        val instance = actionInstance(actionLocation)
            ?: error("Not found: $actionLocation")

        val action = instance as? DetachedDownloadAction
            ?: error("Not DetachedDownloadAction: $actionLocation - $instance")

        return action.executeDownload(request)
    }
}
