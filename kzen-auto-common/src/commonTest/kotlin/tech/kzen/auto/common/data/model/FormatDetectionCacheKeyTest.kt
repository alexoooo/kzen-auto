package tech.kzen.auto.common.data.format.detection

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.lib.common.exec.TextExecutionValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class FormatDetectionCacheKeyTest {
    private val ref = DataRef(DataSourceId("store"), "orders.csv", mapOf("ignored" to "one"))
    private val observed = fingerprint("observed")
    private val hints = NormalizedFormatHints.of("CSV", "text/csv; charset=UTF-8")
    private val candidateA = candidate("formats.yaml#Csv", "csv-v1")
    private val candidateB = candidate("formats.yaml#Semicolon", "semicolon-v1")
    private val policy = DetectionPolicy.default(listOf(
        FormatHintMetadata.structured("csv", listOf("csv"), listOf("text/csv"))))


    @Test
    fun defaultPolicyPinsThePerPartCeilings() {
        assertEquals(256 * 1024, policy.maximumDecodedBytes)
        assertEquals(100, policy.maximumLogicalRecords)
        assertEquals(2_000, policy.timeoutMillis)
    }


    @Test
    fun candidateAndProbeOrderAreNotSemantic() {
        val forward = key(candidates = listOf(candidateA, candidateB), probes = listOf("csv-v1", "text-v1"))
        val reverse = key(candidates = listOf(candidateB, candidateA), probes = listOf("text-v1", "csv-v1"))

        assertEquals(forward, reverse)
        assertEquals(forward.digest(), reverse.digest())
    }


    @Test
    fun everyDetectionDimensionInvalidatesTheKey() {
        val base = key()

        assertNotEquals(base, key(expected = fingerprint("expected")))
        assertNotEquals(base, key(observed = fingerprint("changed")))
        assertNotEquals(base, key(hints = NormalizedFormatHints.of("tsv", "text/csv")))
        assertNotEquals(base, key(explicitEncoding = "windows-1252"))
        assertNotEquals(base, key(candidates = listOf(candidate("formats.yaml#Csv", "csv-v2"))))
        assertNotEquals(base, key(candidates = listOf(candidateA, candidateB)))
        assertNotEquals(base, key(probes = listOf("csv-v2")))
        assertNotEquals(base, key(policy = policy.copy(maximumLogicalRecords = 99)))
    }


    @Test
    fun nonHintReferenceAttributesDoNotChangeIdentity() {
        val changedAttributes = ref.copy(attributes = mapOf("ignored" to "two"))

        assertEquals(key(), key(ref = changedAttributes))
        assertEquals(key().digest(), key(ref = changedAttributes).digest())
    }


    @Test
    fun candidateIdentityIncludesEligibilityAndResolvedReadMetadata() {
        val base = metadata()
        val baseIdentity = DetectionCandidateIdentity.of(base)

        assertNotEquals(baseIdentity, DetectionCandidateIdentity.of(base.copy(
            compatibleStructuredFamilies = listOf("regional-csv"))))
        assertNotEquals(baseIdentity, DetectionCandidateIdentity.of(base.copy(
            automaticAdjustments = true)))
        assertNotEquals(baseIdentity, DetectionCandidateIdentity.of(base.copy(
            resolvedRead = base.resolvedRead.copy(
                contentCodings = listOf(ContentCodingSpec.gzip)))))
    }


    private fun key(
        ref: DataRef = this.ref,
        expected: DataContentFingerprint? = null,
        observed: DataContentFingerprint = this.observed,
        hints: NormalizedFormatHints = this.hints,
        explicitEncoding: String? = null,
        candidates: List<DetectionCandidateIdentity> = listOf(candidateA),
        probes: List<String> = listOf("csv-v1"),
        policy: DetectionPolicy = this.policy
    ): FormatDetectionCacheKey = FormatDetectionCacheKey.of(
        ref, expected, observed, hints, explicitEncoding, candidates, probes, policy)


    private fun fingerprint(value: String): DataContentFingerprint =
        DataContentFingerprint("test", TextExecutionValue(value))


    private fun candidate(reference: String, version: String): DetectionCandidateIdentity =
        DetectionCandidateIdentity(reference, TextExecutionValue(version).digest())


    private fun metadata(): DetectionCandidateMetadata = DetectionCandidateMetadata(
        "formats.yaml#Csv",
        TextExecutionValue("format-v1").digest(),
        listOf("csv"),
        listOf("csv"),
        ResolvedReadSpec(
            ReaderCapabilityIdentity("test", "reader", "v1"),
            listOf(ContentCodingSpec.identity),
            TextExecutionValue("config")))
}
