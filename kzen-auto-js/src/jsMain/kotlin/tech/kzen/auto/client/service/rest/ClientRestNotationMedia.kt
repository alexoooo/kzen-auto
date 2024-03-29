package tech.kzen.auto.client.service.rest

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.DigestCache


class ClientRestNotationMedia(
    private val restClient: ClientRestApi
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentCache = DigestCache<String>(128)


    //-----------------------------------------------------------------------------------------------------------------
    override fun isReadOnly(): Boolean {
        return true
    }


    override suspend fun scan(): NotationScan {
        return restClient.scanNotation()
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
        documentCache.clear()
    }
}