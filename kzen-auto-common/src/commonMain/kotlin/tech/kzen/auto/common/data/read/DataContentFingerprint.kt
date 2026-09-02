package tech.kzen.auto.common.data.read

import kotlinx.serialization.Serializable
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class DataContentFingerprint(
    val identity: String,
    val data: ExecutionValue
): Digestible {
    companion object {
        private const val localAttributesIdentity = "tech.kzen.auto/local-attributes-v1"

        fun localOrNull(ref: DataRef): DataContentFingerprint? {
            if (ref.source != null) {
                return null
            }
            val attributes = ref.fingerprintOrNull() ?: return null
            return DataContentFingerprint(
                localAttributesIdentity,
                MapExecutionValue(mapOf(
                    "ref" to TextExecutionValue(ref.id),
                    DataRef.sizeKey to TextExecutionValue(attributes.first),
                    DataRef.modifiedKey to TextExecutionValue(attributes.second))))
        }
    }

    init {
        require(identity.isNotBlank()) { "Content-fingerprint identity must not be blank" }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(identity)
        sink.addDigestible(data)
    }
}
