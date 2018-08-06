package tech.kzen.auto.client.service

import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Digest


class RestNotationMedia(
        private val restClient: RestClient
) : NotationMedia {
    override suspend fun scan(): Map<ProjectPath, Digest> {
        return restClient.scanQuery()
    }


    override suspend fun read(location: ProjectPath): ByteArray {
        return restClient.readQuery(location)
    }


    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
//        httpGet("$baseUrl/notation/${location.relativeLocation}")
        TODO("not implemented")
    }


    override suspend fun delete(location: ProjectPath) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}