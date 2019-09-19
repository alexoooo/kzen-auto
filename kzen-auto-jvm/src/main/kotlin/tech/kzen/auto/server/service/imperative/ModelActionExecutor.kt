package tech.kzen.auto.server.service.imperative

import tech.kzen.auto.common.paradigm.imperative.api.ControlFlow
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
import tech.kzen.auto.common.paradigm.imperative.service.ActionExecutor
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore


class ModelActionExecutor(
        private val graphStore: LocalGraphStore,
        private val graphCreator: GraphCreator
): ActionExecutor {
    override suspend fun execute(
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val graphDefinition = graphStore
                .graphDefinition()
                .successful
                .filterDefinitions(AutoConventions.serverAllowed)

        val objectGraph = graphCreator
                .createGraph(graphDefinition)

        val instance = objectGraph.objectInstances[actionLocation]?.reference

        val action = instance as ExecutionAction

        return action.perform(imperativeModel)
    }


    override suspend fun control(
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ControlTransition {
        val graphDefinition = graphStore
                .graphDefinition()
                .successful
                .filterDefinitions(AutoConventions.serverAllowed)

        val objectGraph = graphCreator.createGraph(
                graphDefinition)

        val instance = objectGraph.objectInstances[actionLocation]?.reference

        val action = instance as ControlFlow

        val state = imperativeModel.frames.last().states[actionLocation.objectPath]!!

        return action.control(imperativeModel, state.controlState!!)
    }
}