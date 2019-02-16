package tech.kzen.auto.server.service

import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.service.ActionExecutor
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner


class ModelActionExecutor(
        private val modelManager: ModelManager
): ActionExecutor {
//    override suspend fun actionManager(): ActionManager {
//        TODO()
//    }

    override suspend fun execute(actionLocation: ObjectLocation): ExecutionResult {
        val graphStructure = modelManager.graphStructure()

        val graphDefinition = GraphDefiner.define(graphStructure)

        val objectGraph = GraphCreator.createGraph(
                graphStructure, graphDefinition)

        val instance = objectGraph.objects.get(actionLocation)

        val action = instance as AutoAction

        return action.perform()
    }


//    override suspend fun executeResponse(actionLocation: ObjectLocation): ExecutionResultResponse {
//        TODO()
//    }
}