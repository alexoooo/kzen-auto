package tech.kzen.auto.common.data.read


data class TypedDecodePolicy(
    val nullToken: String?,
    val malformedValue: String,
    val fieldOverrides: List<FieldDecodeOverride>
)
