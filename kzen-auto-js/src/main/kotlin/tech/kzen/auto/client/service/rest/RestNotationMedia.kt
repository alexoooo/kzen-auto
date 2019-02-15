package tech.kzen.auto.client.service

import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.util.Digest


class RestNotationMedia(
        private val restClient: ClientRestApi
): NotationMedia {
    override suspend fun scan(): BundleTree<Digest> {
        return restClient.scanNotationPaths()
    }


    override suspend fun read(location: BundlePath): ByteArray {
        return restClient.readNotation(location)
    }


    override suspend fun write(location: BundlePath, bytes: ByteArray) {
//        httpGet("$baseUrl/notation/${location.relativeLocation}")
        TODO("not implemented")
    }


    override suspend fun delete(location: BundlePath) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}