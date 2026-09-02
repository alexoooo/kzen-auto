package tech.kzen.auto.common.data.read

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class ContentCodingSpec(
    val identity: String,
    val config: ExecutionValue = MapExecutionValue(emptyMap()),
    val configDigest: Digest = config.digest()
): Digestible {
    companion object {
        val identity = ContentCodingSpec("identity")
        val gzip = ContentCodingSpec("gzip")
    }

    init {
        require(identity.isNotBlank()) { "Content-coding identity must not be blank" }
        require(configDigest == config.digest()) { "Content-coding config digest does not match its data" }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(identity)
        sink.addDigest(configDigest)
    }
}
