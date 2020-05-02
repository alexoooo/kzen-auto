package tech.kzen.auto.server.service.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.detached.service.DetachedExecutor
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore


class ModelDetachedExecutor(
        private val graphStore: LocalGraphStore,
        private val graphCreator: GraphCreator
): DetachedExecutor {
    override suspend fun execute(
            actionLocation: ObjectLocation,
            request: DetachedRequest
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
            ExecutionFailure(t.message ?: "exception")
        }
    }
}