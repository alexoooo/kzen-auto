package tech.kzen.auto.client.service.rest

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.service.media.NotationMedia


class ClientRestNotationMedia(
        private val restClient: ClientRestApi
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        return restClient.scanNotationPaths()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath): String {
        return restClient.readNotation(documentPath)
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
//        httpGet("$baseUrl/notation/${location.relativeLocation}")
        TODO("not implemented")
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        TODO("not implemented")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        TODO("not implemented")
    }


    override suspend fun readResource(resourceLocation: ResourceLocation): ByteArray {
        TODO("not implemented")
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ByteArray) {
        TODO("not implemented")
    }
}