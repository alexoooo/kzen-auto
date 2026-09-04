package tech.kzen.auto.common.data.format.detection

import kotlinx.serialization.Serializable
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class DetectionCandidateMetadata(
    val formatReference: String,
    val formatDigest: Digest,
    val exactExtensions: List<String>,
    val compatibleStructuredFamilies: List<String>,
    val resolvedRead: ResolvedReadSpec,
    val automaticAdjustments: Boolean = false,
    val authoringCapabilityIdentity: String? = null,
    val overrideEditorReference: String? = null,
    val columnsLocked: Boolean = false
): Digestible {
    init {
        require(formatReference.isNotBlank()) { "Detection-candidate format reference must not be blank" }
        require(exactExtensions == exactExtensions
            .map { it.trim().removePrefix(".").lowercase() }.filter(String::isNotEmpty).distinct().sorted()) {
            "Detection-candidate extensions must be normalized"
        }
        require(compatibleStructuredFamilies ==
            compatibleStructuredFamilies.map { it.trim().lowercase() }
                .filter(String::isNotEmpty).distinct().sorted()) {
            "Detection-candidate structured families must be normalized"
        }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(formatReference)
        sink.addDigest(formatDigest)
        sink.addInt(exactExtensions.size)
        exactExtensions.forEach(sink::addUtf8)
        sink.addInt(compatibleStructuredFamilies.size)
        compatibleStructuredFamilies.forEach(sink::addUtf8)
        sink.addDigestible(resolvedRead)
        sink.addBoolean(automaticAdjustments)
        sink.addUtf8Nullable(authoringCapabilityIdentity)
        sink.addUtf8Nullable(overrideEditorReference)
        sink.addBoolean(columnsLocked)
    }
}
