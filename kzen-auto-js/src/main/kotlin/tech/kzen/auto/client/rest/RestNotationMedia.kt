package tech.kzen.auto.client.rest

//import tech.kzen.lib.client.util.ClientRestUtils.httpGet
import tech.kzen.auto.client.util.httpGet
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia


class RestNotationMedia(
        private val baseUrl: String
) : NotationMedia {
    override suspend fun read(location: ProjectPath): ByteArray {
        val response = httpGet("$baseUrl/notation/${location.relativeLocation}")

        // from kotlinx.serialization String.toUtf8Bytes
        val blck = js("unescape(encodeURIComponent(response))")
        return (blck as String).toList().map { it.toByte() }.toByteArray()
    }


    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
//        httpGet("$baseUrl/notation/${location.relativeLocation}")
        TODO("not implemented")
    }
}