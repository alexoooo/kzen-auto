package tech.kzen.auto.server.service

import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.common.service.ActionExecutor
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner


class ModelActionExecutor(
        private val modelManager: ModelManager
): ActionExecutor {
    override suspend fun execute(actionName: String) {
        val projectModel = modelManager.projectModel()

        val graphDefinition = ObjectGraphDefiner.define(
                projectModel.projectNotation, projectModel.graphMetadata)

        val objectGraph = ObjectGraphCreator.createGraph(
                graphDefinition, projectModel.graphMetadata)

        val instance = objectGraph.get(actionName)

        val action = instance as AutoAction

        action.perform()
    }
}