package tech.kzen.auto.platform


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object UrlUtils {
    fun decodeUrlComponent(urlComponent: String): String
}