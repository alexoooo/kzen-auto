package tech.kzen.auto.common.data.format.detection

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class FormatRefHintIdentity(
    val source: String?,
    val id: String
): Digestible {
    init {
        require(id.isNotBlank()) { "Format-reference hint identity must not be blank" }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8Nullable(source)
        sink.addUtf8(id)
    }
}
