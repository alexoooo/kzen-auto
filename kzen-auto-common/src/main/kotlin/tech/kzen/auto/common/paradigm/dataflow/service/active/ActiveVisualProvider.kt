package tech.kzen.auto.common.paradigm.dataflow.service.active

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowProvider
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class ActiveVisualProvider(
        private val activeDataflowManager: ActiveDataflowManager
):
        VisualDataflowProvider
{
    override suspend fun inspect(
            host: DocumentPath
    ): VisualDataflowModel {
        return activeDataflowManager.inspect(host)
    }


    override suspend fun execute(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition {
        return activeDataflowManager.executeVisual(host, vertexLocation)
    }


    override suspend fun reset(host: DocumentPath): VisualDataflowModel {
        return activeDataflowManager.reset(host)
    }
}