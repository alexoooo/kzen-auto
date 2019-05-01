package tech.kzen.auto.server.service.imperative

import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.service.ActionExecutor
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.model.locate.ObjectLocation


class ModelActionExecutor(
        private val graphStructureManager: GraphStructureManager
): ActionExecutor {
    override suspend fun execute(actionLocation: ObjectLocation): ImperativeResult {
        val graphStructure = graphStructureManager.graphStructure()

        val graphDefinition = GraphDefiner.define(graphStructure)

        val objectGraph = GraphCreator.createGraph(
                graphStructure, graphDefinition)

        val instance = objectGraph.objects.get(actionLocation)

        val action = instance as ExecutionAction

        return action.perform()
    }
}