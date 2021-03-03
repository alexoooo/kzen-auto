package tech.kzen.auto.platform


actual class DataLocation(
    val uri: String
) {
    actual fun asString(): String {
        return uri
    }
}