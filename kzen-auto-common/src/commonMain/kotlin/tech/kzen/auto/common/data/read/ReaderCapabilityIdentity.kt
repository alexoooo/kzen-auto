package tech.kzen.auto.common.data.read

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class ReaderCapabilityIdentity(
    val namespace: String,
    val name: String,
    val compatibility: String
): Digestible {
    init {
        require(namespace.isNotBlank()) { "Reader namespace must not be blank" }
        require(name.isNotBlank()) { "Reader name must not be blank" }
        require(compatibility.isNotBlank()) { "Reader compatibility must not be blank" }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(namespace)
        sink.addUtf8(name)
        sink.addUtf8(compatibility)
    }
}
