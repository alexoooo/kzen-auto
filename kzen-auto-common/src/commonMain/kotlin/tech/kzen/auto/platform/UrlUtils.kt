package tech.kzen.auto.platform


expect object UrlUtils {
    fun decodeUrlComponent(urlComponent: String): String
}