package tech.kzen.auto.server.data.read.delimited

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tech.kzen.auto.common.data.format.detection.DetectionPolicy
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.DelimitedDialectSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.common.data.read.TypedDecodePolicy
import tech.kzen.auto.plugin.api.data.ReaderProbeRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeResult
import tech.kzen.auto.plugin.api.data.ReaderProbeObserver
import tech.kzen.auto.plugin.api.data.ReaderProbeStrength
import tech.kzen.auto.plugin.api.data.StrictCharacterView
import tech.kzen.lib.common.util.ImmutableByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith


class DelimitedProbeTest {
    private val policy = DetectionPolicy(4096, 100, 2_000, emptyList(), listOf("UTF-8"))

    @Test
    fun contentMatchRequiresConsistentMultiColumnRecordsAndInfersTypedHeader() = runBlocking {
        val matched = assertIs<ReaderProbeResult.Matched>(probe("name,count\nalice,1\nbob,2"))
        assertEquals(ReaderProbeStrength.ContentStrong, matched.strength)
        assertEquals("present", (matched.canonicalConfig as DelimitedReadConfig).header.policy)

        assertIs<ReaderProbeResult.NoMatch>(probe("one line only"))
        assertIs<ReaderProbeResult.NoMatch>(probe("a,b\nc"))
        Unit
    }

    @Test
    fun allTextTableRetainsRowOneWithPositionalLabels() = runBlocking {
        val matched = assertIs<ReaderProbeResult.Matched>(probe("Alice,Toronto\nBob,Ottawa"))
        assertEquals("infer-labels", (matched.canonicalConfig as DelimitedReadConfig).header.policy)
    }

    @Test
    fun exactHintAllowsHeaderOnlyAndRejectsContainedSyntax() = runBlocking {
        val hints = NormalizedFormatHints.of(filenameExtension = "csv")
        val matched = assertIs<ReaderProbeResult.Matched>(probe(
            "name,value\n", hints, structuredHint = true, exactExtension = true))
        assertEquals(ReaderProbeStrength.ExtensionValidated, matched.strength)
        assertIs<ReaderProbeResult.Rejected>(probe(
            "name,value\n\"bad", hints, structuredHint = true, exactExtension = true))
        Unit
    }

    @Test
    fun familyCompatibleCandidateNeedsStrongContentEvidence() = runBlocking {
        val hints = NormalizedFormatHints.of(filenameExtension = "csv")
        assertIs<ReaderProbeResult.NoMatch>(probe(
            "name,count\nalice,1",
            hints,
            structuredHint = true,
            exactExtension = false,
            configured = config(delimiter = ";")))
        assertIs<ReaderProbeResult.NoMatch>(probe(
            "name;count\nalice;1",
            hints,
            structuredHint = true,
            exactExtension = true,
            configured = config(delimiter = ",")))
        Unit
    }

    @Test
    fun authoredCandidateKeepsItsFramingAndBomDeclaration() = runBlocking {
        val hints = NormalizedFormatHints.of(filenameExtension = "csv")
        assertIs<ReaderProbeResult.Rejected>(probe(
            "name,value\r\na,1",
            hints,
            structuredHint = true,
            exactExtension = true,
            allowCanonicalAdjustments = false))
        assertIs<ReaderProbeResult.Rejected>(probe(
            "name,value\na,1",
            hints,
            structuredHint = true,
            exactExtension = true,
            allowCanonicalAdjustments = false,
            configured = config(bom = "require")))
        Unit
    }

    @Test
    fun coroutineCancellationStopsAProbeInsideOneLongLogicalRecord() = runBlocking {
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            withTimeout(1) {
                withContext(Dispatchers.Default) {
                    probe("\"" + "x".repeat(5_000_000), endOfInput = false)
                }
            }
        }
        Unit
    }

    @Test
    fun quotedNewlineIsOneRecordAndCutoffFinalRecordIsIgnored() = runBlocking {
        val quoted = assertIs<ReaderProbeResult.Matched>(probe("name,value\n\"multi\nline\",1\nx,2"))
        assertEquals("present", (quoted.canonicalConfig as DelimitedReadConfig).header.policy)

        assertIs<ReaderProbeResult.NoMatch>(probe("a,b\n\"partial", endOfInput = false))
        Unit
    }


    @Test
    fun logicalRecordLimitCapsTheCandidateEvidence() = runBlocking {
        val limited = policy.copy(maximumLogicalRecords = 3)
        val text = (0 until 20).joinToString("\n") { index -> "name-$index,$index" }
        val matched = assertIs<ReaderProbeResult.Matched>(ConfiguredDelimitedReaderCapability.probe(
            ReaderProbeRequest(
                config(),
                NormalizedFormatHints.empty,
                ImmutableByteArray.copyOf(text.encodeToByteArray()),
                listOf(StrictCharacterView("UTF-8", text)),
                true,
                limited,
                false,
                false,
                true)))

        kotlin.test.assertTrue(matched.evidence.startsWith("3 complete records"), matched.evidence)
    }


    @Test
    fun logicalRecordLimitIsSharedAcrossAllAttemptsForOneCandidate() = runBlocking {
        val limited = policy.copy(maximumLogicalRecords = 3)
        var considered = 0
        val result = ConfiguredDelimitedReaderCapability.probe(ReaderProbeRequest(
            config(),
            NormalizedFormatHints.empty,
            ImmutableByteArray.copyOf("first\nsecond\nthird\nfourth".encodeToByteArray()),
            listOf(
                StrictCharacterView("UTF-8", "first\nsecond\nthird\nfourth"),
                StrictCharacterView("windows-1252", "name,count\nalice,1\nbob,2")),
            true,
            limited,
            false,
            false,
            true,
            ReaderProbeObserver { considered += it }))

        assertIs<ReaderProbeResult.NoMatch>(result)
        assertEquals(3, considered)
    }

    private suspend fun probe(
        text: String,
        hints: NormalizedFormatHints = NormalizedFormatHints.empty,
        endOfInput: Boolean = true,
        structuredHint: Boolean = false,
        exactExtension: Boolean = false,
        allowCanonicalAdjustments: Boolean = true,
        configured: DelimitedReadConfig = config()
    ): ReaderProbeResult = ConfiguredDelimitedReaderCapability.probe(ReaderProbeRequest(
        configured,
        hints,
        ImmutableByteArray.copyOf(text.encodeToByteArray()),
        listOf(StrictCharacterView("UTF-8", text)),
        endOfInput,
        policy,
        structuredHint,
        exactExtension,
        allowCanonicalAdjustments))

    private fun config(
        delimiter: String = ",",
        bom: String = "permit"
    ) = DelimitedReadConfig(
        RecordFramingSpec("lf"),
        DelimitedDialectSpec(delimiter, "\"", "double-quote", "empty", "none"),
        HeaderReadSpec("present", "exact-name"),
        CharacterDecodingSpec("UTF-8", bom, "report", "report"),
        null,
        TypedDecodePolicy(null, "fail-part", emptyList()))
}
