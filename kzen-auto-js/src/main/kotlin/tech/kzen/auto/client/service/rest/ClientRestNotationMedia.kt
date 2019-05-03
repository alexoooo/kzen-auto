package tech.kzen.auto.client.service.rest

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.util.Digest


class ClientRestNotationMedia(
        private val restClient: ClientRestApi
): NotationMedia {
    override suspend fun scan(): DocumentPathMap<Digest> {
        return restClient.scanNotationPaths()
    }


    override suspend fun read(location: DocumentPath): ByteArray {
        return restClient.readNotation(location)
    }


    override suspend fun write(location: DocumentPath, bytes: ByteArray) {
//        httpGet("$baseUrl/notation/${location.relativeLocation}")
        TODO("not implemented")
    }


    override suspend fun delete(location: DocumentPath) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}