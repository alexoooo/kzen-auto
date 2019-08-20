package tech.kzen.auto.server.service.imperative

import tech.kzen.auto.common.paradigm.imperative.api.ControlFlow
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
import tech.kzen.auto.common.paradigm.imperative.service.ActionExecutor
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.model.locate.ObjectLocation


class ModelActionExecutor(
        private val graphStructureManager: GraphStructureManager
): ActionExecutor {
    override suspend fun execute(
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val graphStructure = graphStructureManager.serverGraphStructure()

        val graphDefinition = GraphDefiner.define(graphStructure)

        val objectGraph = GraphCreator.createGraph(
                graphStructure, graphDefinition)

        val instance = objectGraph.objects[actionLocation]?.reference

        val action = instance as ExecutionAction

        return action.perform(imperativeModel)
    }


    override suspend fun control(
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ControlTransition {
        val graphStructure = graphStructureManager.serverGraphStructure()

        val graphDefinition = GraphDefiner.define(graphStructure)

        val objectGraph = GraphCreator.createGraph(
                graphStructure, graphDefinition)

        val instance = objectGraph.objects[actionLocation]?.reference

        val action = instance as ControlFlow

        val state = imperativeModel.frames.last().states[actionLocation.objectPath]!!

        return action.control(imperativeModel, state.controlState!!)
    }
}