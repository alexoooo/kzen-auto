package tech.kzen.auto.common.data.format.detection

import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@ConsistentCopyVisibility
data class FormatDetectionCacheKey private constructor(
    val ref: FormatRefHintIdentity,
    val expectedFingerprint: DataContentFingerprint?,
    val observedFingerprint: DataContentFingerprint,
    val hints: NormalizedFormatHints,
    val explicitEncoding: String?,
    val candidates: List<DetectionCandidateIdentity>,
    val probeCompatibilityIdentities: List<String>,
    val policyDigest: Digest
): Digestible {
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8Nullable(ref.source)
        sink.addUtf8(ref.id)
        sink.addDigestibleNullable(expectedFingerprint)
        sink.addDigestible(observedFingerprint)
        sink.addDigestible(hints)
        sink.addUtf8Nullable(explicitEncoding)
        sink.addDigestibleList(candidates)
        sink.addInt(probeCompatibilityIdentities.size)
        probeCompatibilityIdentities.forEach(sink::addUtf8)
        sink.addDigest(policyDigest)
    }

    companion object {
        fun of(
            ref: tech.kzen.auto.common.data.model.DataRef,
            expectedFingerprint: DataContentFingerprint?,
            observedFingerprint: DataContentFingerprint,
            hints: NormalizedFormatHints,
            explicitEncoding: String?,
            candidates: Collection<DetectionCandidateIdentity>,
            probeCompatibilityIdentities: Collection<String>,
            policy: DetectionPolicy
        ): FormatDetectionCacheKey = FormatDetectionCacheKey(
            FormatRefHintIdentity(ref.source?.value, ref.id),
            expectedFingerprint,
            observedFingerprint,
            hints,
            explicitEncoding?.trim()?.lowercase(),
            candidates.distinct().sortedWith(
                compareBy(DetectionCandidateIdentity::formatReference)
                    .thenBy { it.candidateDigest.asString() }),
            probeCompatibilityIdentities.distinct().sorted(),
            policy.digest())
    }
}
