package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowInitializer
import tech.kzen.lib.common.model.document.DocumentPath


class ClientRestVisualDataflowInitializer(
        private val restClient: ClientRestApi
): VisualDataflowInitializer {
    override suspend fun initialModel(host: DocumentPath): VisualDataflowModel {
        return restClient.visualDataflowModel(host)
    }
}