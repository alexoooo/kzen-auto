package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowProvider
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class ClientRestVisualDataflowProvider(
        private val restClient: ClientRestApi
): VisualDataflowProvider {
    override suspend fun inspect(
            host: DocumentPath
    ): VisualDataflowModel {
        return restClient.visualDataflowModel(host)
    }


    override suspend fun execute(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition {
        check(host == vertexLocation.documentPath) {
            "External host not supported (yet): $host - $vertexLocation"
        }

        return restClient.execDataflow(vertexLocation)
    }


    override suspend fun reset(
            host: DocumentPath
    ): VisualDataflowModel {
        return restClient.resetDataflowExecution(host)
    }
}