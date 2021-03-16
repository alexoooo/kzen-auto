package tech.kzen.auto.platform

import org.w3c.dom.url.URL
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


// see: https://developer.mozilla.org/en-US/docs/Web/API/URL
actual class Url(
    private val url: URL
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    actual companion object {
        private const val filePrefix = "file://"
        private const val localFilePrefix = "file:///"
        private const val networkFilePrefix = "file:////"


        actual fun of(url: String): Url {
            return Url(URL(url))
        }

        actual fun parse(url: String): Url? {
            val parsed =
                try {
                    URL(url)
                }
                catch (e: Exception) {
                    return null
                }

            return Url(parsed)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    actual val scheme: String
        get() = url.protocol.dropLast(1)


    actual val path: String
        get() =
            if (isFile() && isNetworkFile()) {
                "//" + url.hostname + url.pathname
            }
            else {
                url.pathname
            }


    actual val query: String?
        get() {
            return when (url.search.length) {
                in 0 .. 1 -> null
                else -> url.search.substring(1)
            }
        }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isFile(): Boolean {
        return scheme == "file"
    }


    private fun isNetworkFile(href: String = url.href): Boolean {
        return href.startsWith(filePrefix) && ! href.startsWith(localFilePrefix)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        val href = url.href

        return when {
            isNetworkFile(href) -> {
                val networkPath = href.substring(filePrefix.length)
                "$networkFilePrefix$networkPath"
            }

            else ->
                href
        }
    }


    override fun digest(builder: Digest.Builder) {
        builder.addUtf8(toString())
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as Url

        if (url != other.url) return false

        return true
    }


    override fun hashCode(): Int {
        return url.hashCode()
    }
}