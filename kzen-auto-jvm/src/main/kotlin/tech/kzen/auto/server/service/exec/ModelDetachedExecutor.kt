package tech.kzen.auto.server.service.exec

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadAction
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadExecutor
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.LocalGraphStore


class ModelDetachedExecutor(
    private val graphStore: LocalGraphStore,
    private val graphInstanceCache: GraphInstanceCache
):
    DetachedDownloadExecutor
{
    companion object {
        private val logger = LoggerFactory.getLogger(ModelDetachedExecutor::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class ActionLookup(
        val definitionAttempt: GraphDefinitionAttempt,
        val instanceAttempt: ObjectInstanceAttempt
    ) {
        fun reference(): Any? {
            return (instanceAttempt as? ObjectInstanceAttempt.Created)?.objectInstance?.reference
        }


        fun errorMessage(actionLocation: ObjectLocation): String {
            return ExecutionGraphErrors.describe(actionLocation, definitionAttempt, instanceAttempt)
        }
    }


    // The action is instantiated from its own transitive definition closure (of the serverAllowed
    // subset - the policy filter comes first) and reused across calls while its notation is unchanged.
    // The definition attempt rides along so a miss can name its origin instead of a bare "Not found".
    private suspend fun actionLookup(actionLocation: ObjectLocation): ActionLookup {
        val definitionAttempt = graphStore.graphDefinition()

        val serverDefinition = definitionAttempt
            .transitiveSuccessful
            .filterDefinitions(AutoConventions.serverAllowed)

        return ActionLookup(
            definitionAttempt,
            graphInstanceCache.tryObjectInstance(serverDefinition, actionLocation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun execute(
        actionLocation: ObjectLocation,
        request: ExecutionRequest
    ): ExecutionResult {
        val lookup = actionLookup(actionLocation)

        val instance = lookup.reference()
            ?: return ExecutionFailure(lookup.errorMessage(actionLocation))

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
        val lookup = actionLookup(actionLocation)

        val instance = lookup.reference()
            ?: error(lookup.errorMessage(actionLocation))

        val action = instance as? DetachedDownloadAction
            ?: error("Not DetachedDownloadAction: $actionLocation - $instance")

        return action.executeDownload(request)
    }
}
