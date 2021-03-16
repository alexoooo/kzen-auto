package tech.kzen.auto.platform

import java.net.URLDecoder


actual object UrlUtils {
    actual fun decodeUrlComponent(urlComponent: String): String {
        // see: https://stackoverflow.com/questions/2632175/decoding-uri-query-string-in-java
        return URLDecoder.decode(urlComponent.replace("+", "%2B"), Charsets.UTF_8)
    }
}