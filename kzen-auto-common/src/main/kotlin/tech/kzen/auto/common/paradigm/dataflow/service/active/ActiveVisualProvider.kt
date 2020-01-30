package tech.kzen.auto.common.paradigm.dataflow.service.active

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowProvider
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class ActiveVisualProvider(
        private val activeDataflowRepository: ActiveDataflowRepository
):
        VisualDataflowProvider
{
    override suspend fun inspectDataflow(
            host: DocumentPath
    ): VisualDataflowModel {
        return activeDataflowRepository.inspect(host)
    }


    override suspend fun inspectVertex(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexModel {
        return activeDataflowRepository.inspectVertex(host, vertexLocation)
    }


    override suspend fun executeVertex(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition {
        return activeDataflowRepository.executeVisual(host, vertexLocation)
    }


    override suspend fun resetDataflow(host: DocumentPath): VisualDataflowModel {
        return activeDataflowRepository.reset(host)
    }
}