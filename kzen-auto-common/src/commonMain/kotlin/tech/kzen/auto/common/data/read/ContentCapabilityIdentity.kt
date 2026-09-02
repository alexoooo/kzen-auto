package tech.kzen.auto.common.data.read

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class ContentCapabilityIdentity(
    val namespace: String,
    val name: String,
    val compatibility: String
): Digestible {
    companion object {
        val sequentialBytes = ContentCapabilityIdentity(
            "tech.kzen.auto", "sequential-bytes", "1")
    }

    init {
        require(namespace.isNotBlank()) { "Content-capability namespace must not be blank" }
        require(name.isNotBlank()) { "Content-capability name must not be blank" }
        require(compatibility.isNotBlank()) { "Content-capability compatibility must not be blank" }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(namespace)
        sink.addUtf8(name)
        sink.addUtf8(compatibility)
    }
}
