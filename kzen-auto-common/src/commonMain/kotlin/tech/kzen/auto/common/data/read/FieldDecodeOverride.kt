package tech.kzen.auto.common.data.read


data class FieldDecodeOverride(
    val path: List<String>,
    val nullToken: String?
)
