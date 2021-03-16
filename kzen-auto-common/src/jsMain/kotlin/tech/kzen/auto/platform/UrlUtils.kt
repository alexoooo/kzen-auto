package tech.kzen.auto.platform


external fun encodeURI(str: String): String
external fun encodeURIComponent(str: String): String

external fun decodeURI(str: String): String
external fun decodeURIComponent(str: String): String


actual object UrlUtils {
    actual fun decodeUrlComponent(urlComponent: String): String {
        return decodeURIComponent(urlComponent)
    }
}