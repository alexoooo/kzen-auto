package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowProvider
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation


class ClientRestVisualDataflowProvider(
        private val restClient: ClientRestApi
): VisualDataflowProvider {
    override suspend fun inspectDataflow(
            host: DocumentPath
    ): VisualDataflowModel {
        return restClient.visualDataflowModel(host)
    }


    override suspend fun inspectVertex(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexModel {
        return restClient.visualVertexModel(host, vertexLocation)
    }


    override suspend fun executeVertex(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition {
        check(host == vertexLocation.documentPath) {
            "External host not supported (yet): $host - $vertexLocation"
        }

        return restClient.execDataflow(vertexLocation)
    }


    override suspend fun resetDataflow(
            host: DocumentPath
    ): VisualDataflowModel {
        return restClient.resetDataflowExecution(host)
    }
}