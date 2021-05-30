package tech.kzen.auto.platform

import org.w3c.dom.url.URL
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


// see: https://developer.mozilla.org/en-US/docs/Web/API/URL
actual class Url(
    private val url: URL,
    private val networkFile: Boolean
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    actual companion object {
        private const val filePrefix = "file://"
        private const val localFilePrefix = "file:///"
        private const val networkFilePrefix = "file:////"


        actual fun of(url: String): Url {
            return Url(URL(url), url.startsWith(networkFilePrefix))
        }

        actual fun parse(url: String): Url? {
            @Suppress("LiftReturnOrAssignment")
            try {
                return of(url)
            }
            catch (e: Exception) {
                return null
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    actual val scheme: String
        get() = url.protocol.dropLast(1)


    actual val path: String
        get() =
            if (isFile() && networkFile) {
                if (url.hostname == "" && url.pathname.startsWith("/")) {
                    "/" + url.pathname
                }
                else {
                    "//" + url.hostname + url.pathname
                }
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


//    private fun isNetworkFile(href: String = url.href): Boolean {
//        return href.startsWith(filePrefix) && ! href.startsWith(localFilePrefix)
//    }


//    actual fun normalize(): Url {
//        return this
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        val href = url.href

        return when {
            networkFile -> {
                when {
                    href.startsWith(networkFilePrefix) ->
                        href

                    href.startsWith(localFilePrefix) ->
                        "$networkFilePrefix${href.substring(localFilePrefix.length)}"

                    href.startsWith(filePrefix) ->
                        "$networkFilePrefix${href.substring(filePrefix.length)}"

                    else ->
                        throw IllegalStateException("Network file expected: $href")
                }
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