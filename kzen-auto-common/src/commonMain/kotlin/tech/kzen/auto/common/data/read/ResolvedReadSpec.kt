package tech.kzen.auto.common.data.read

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import kotlin.jvm.JvmOverloads


@Serializable
data class ResolvedReadSpec @JvmOverloads constructor(
    val reader: ReaderCapabilityIdentity,
    val contentCodings: List<ContentCodingSpec>,
    val config: ExecutionValue,
    val configDigest: Digest = config.digest()
): Digestible {
    init {
        require(configDigest == config.digest()) { "Reader config digest does not match its data" }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(reader)
        sink.addDigestibleList(contentCodings)
        sink.addDigest(configDigest)
    }
}
