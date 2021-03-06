package tech.kzen.auto.server.service.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.api.ScriptControl
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
import tech.kzen.auto.common.paradigm.imperative.service.ActionExecutor
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore


class ModelActionExecutor(
        private val graphStore: LocalGraphStore,
        private val graphCreator: GraphCreator
): ActionExecutor {
    override suspend fun execute(
            host: DocumentPath,
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        // TODO: report any definition issues on client side
        val graphDefinition = graphStore
                .graphDefinition()
                .transitiveSuccessful()
                .filterDefinitions(AutoConventions.serverAllowed)

        // TODO: add GraphInstanceAttempt for error reporting
        val graphInstance =
                graphCreator.createGraph(graphDefinition)

        val instance = graphInstance.objectInstances[actionLocation]?.reference
                ?: throw IllegalArgumentException("Not found: $actionLocation")

        val action = instance as ScriptStep

        return action.perform(imperativeModel, graphInstance)
    }


    override suspend fun control(
            host: DocumentPath,
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ControlTransition {
        val graphDefinition = graphStore
                .graphDefinition()
                .transitiveSuccessful()
                .filterDefinitions(AutoConventions.serverAllowed)

        val objectGraph = graphCreator.createGraph(
                graphDefinition)

        val instance = objectGraph.objectInstances[actionLocation]?.reference

        val action = instance as ScriptControl

        val state = imperativeModel.frames.last().states[actionLocation.objectPath]!!

        return action.control(imperativeModel, state.controlState!!)
    }


    override suspend fun returnFrame(host: DocumentPath) {}
}