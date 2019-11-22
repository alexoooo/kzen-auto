package tech.kzen.auto.client.service.rest

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.DigestCache
import tech.kzen.lib.common.util.ImmutableByteArray


class ClientRestNotationMedia(
        private val restClient: ClientRestApi
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentCache = DigestCache<String>(128)


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        return restClient.scanNotationPaths()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
        if (expectedDigest != null) {
            val cached = documentCache.get(expectedDigest)
            if (cached != null) {
                return cached
            }
        }

        val body = restClient.readNotation(documentPath)

        val digest = Digest.ofUtf8(body)
        documentCache.put(digest, body)

        return body
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
//        restClient.deleteResource(resourceLocation)
        TODO("not implemented")
    }


    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        return restClient.readResource(resourceLocation)
    }


    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        TODO("not implemented")
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
//        restClient.writeResource(resourceLocation, contents)
        TODO("not implemented")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun invalidate() {
        // NB: no caching here
    }
}