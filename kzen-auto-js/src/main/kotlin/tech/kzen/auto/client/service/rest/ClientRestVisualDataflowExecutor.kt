package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowExecutor
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class ClientRestVisualDataflowExecutor(
        private val restClient: ClientRestApi
): VisualDataflowExecutor {
    override suspend fun execute(host: DocumentPath, vertexLocation: ObjectLocation): VisualVertexTransition {
        check(host == vertexLocation.documentPath) {
            "External host not supported (yet): $host - $vertexLocation"
        }

        return restClient.execDataflow(vertexLocation)
    }
}