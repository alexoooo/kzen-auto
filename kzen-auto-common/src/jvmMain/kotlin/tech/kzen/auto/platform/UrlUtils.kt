package tech.kzen.auto.platform

import java.net.URLDecoder


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object UrlUtils {
    actual fun decodeUrlComponent(urlComponent: String): String {
        // see: https://stackoverflow.com/questions/2632175/decoding-uri-query-string-in-java
        return URLDecoder.decode(urlComponent.replace("+", "%2B"), Charsets.UTF_8)
    }
}