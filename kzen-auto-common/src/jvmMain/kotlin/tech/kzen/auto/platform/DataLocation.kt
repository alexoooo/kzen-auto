package tech.kzen.auto.platform

import java.net.URI


actual class DataLocation(
    val uri: URI
) {
    actual fun asString(): String {
        return uri.toString()
    }
}