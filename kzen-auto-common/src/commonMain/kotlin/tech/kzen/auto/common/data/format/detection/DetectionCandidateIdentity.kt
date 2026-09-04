package tech.kzen.auto.common.data.format.detection

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class DetectionCandidateIdentity(
    val formatReference: String,
    val candidateDigest: Digest
): Digestible {
    init {
        require(formatReference.isNotBlank()) { "Candidate format reference must not be blank" }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(formatReference)
        sink.addDigest(candidateDigest)
    }


    companion object {
        fun of(metadata: DetectionCandidateMetadata): DetectionCandidateIdentity =
            DetectionCandidateIdentity(metadata.formatReference, metadata.digest())
    }
}
