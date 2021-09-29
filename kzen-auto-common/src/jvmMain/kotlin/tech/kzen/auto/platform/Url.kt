package tech.kzen.auto.platform

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import java.net.URI


actual class Url(
    private val uri: URI
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    actual companion object {
        actual fun of(url: String): Url {
            return Url(URI.create(url))
        }

        actual fun parse(url: String): Url? {
            val parsed =
                try {
                    URI(url)
                }
                catch (e: Exception) {
                    return null
                }

            return Url(parsed)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    actual val scheme: String
        get() = uri.scheme


    actual val path: String
        get() = uri.path ?: uri.schemeSpecificPart.substringBefore("?")


    actual val query: String?
        get() {
            return uri.schemeSpecificPart.substringAfter("?", "").ifBlank { null }
        }


    fun normalize(): Url {
        return Url(uri.normalize())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return uri.toString()
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(toString())
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Url

        if (uri != other.uri) return false

        return true
    }


    override fun hashCode(): Int {
        return uri.hashCode()
    }
}