package tech.kzen.auto.platform


external fun encodeURI(str: String): String
external fun encodeURIComponent(str: String): String

external fun decodeURI(str: String): String
external fun decodeURIComponent(str: String): String


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object UrlUtils {
    actual fun decodeUrlComponent(urlComponent: String): String {
        return decodeURIComponent(urlComponent)
    }
}