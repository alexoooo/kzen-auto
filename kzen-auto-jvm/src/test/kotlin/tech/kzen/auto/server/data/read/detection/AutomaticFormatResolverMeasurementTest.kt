package tech.kzen.auto.server.data.read.detection

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.data.content.DirectDataContext
import tech.kzen.auto.server.data.content.FakeObjectStoreProvider
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.fakeFingerprint
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.server.data.read.text.PlainTextReaderCapability
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class AutomaticFormatResolverMeasurementTest {
    @Test
    fun representativeColdAndWarmDetectionWorkIsMeasuredWithoutExtraReads() = runBlocking {
        val comma = "name,count\nalice,1\nbob,2\n".encodeToByteArray()
        val semicolon = "name;count\nalice;1\nbob;2\n".encodeToByteArray()
        val plainText = "ordinary notes\nsecond line\n".encodeToByteArray()
        val scenarios = listOf(
            Scenario("Comma CSV", "orders.csv", "csv", comma, comma,
                listOf(ContentCodingSpec.identity), ","),
            Scenario("Regional semicolon CSV", "regional.csv.gz", "gz", gzip(semicolon), semicolon,
                listOf(ContentCodingSpec.identity, ContentCodingSpec.gzip), ";"),
            Scenario("Plain text", "notes.txt", "txt", plainText, plainText,
                listOf(ContentCodingSpec.identity), null))

        val context = KzenAutoContext.forTest()
        try {
            println("| Scenario | Phase | Decoded bytes | Acquisition codings | Complete records by candidate | Elapsed ms | Cache state |")
            println("|---|---:|---:|---|---|---:|---|")
            for ((index, scenario) in scenarios.withIndex()) {
                measure(context, scenario, index)
            }
        }
        finally {
            context.close()
        }
    }


    private suspend fun measure(context: KzenAutoContext, scenario: Scenario, index: Int) {
        val source = DataSourceId("detection-measurement-$index")
        val fingerprint = fakeFingerprint("measurement-$index")
        val provider = FakeObjectStoreProvider(scenario.storedBytes, fingerprint)
        val observations = mutableListOf<AutomaticFormatResolutionObservation>()
        val resolver = AutomaticFormatResolver(
            context.configuredRecordFormatRegistry,
            context.readerCapabilityRegistry,
            DetectionSampleAcquirer(SequentialContentStack(DataContentProviderLookup(
                LocalDataContentProvider(), mapOf(source to provider)))),
            observer = AutomaticFormatResolverObserver(observations::add))
        val request = FormatResolutionRequest(
            DirectDataContext,
            DataRef(source, scenario.id),
            fingerprint,
            NormalizedFormatHints.of(filenameExtension = scenario.extension),
            null)

        val coldResult = resolver.resolve(request)
        val acquisitionsAfterCold = provider.acquireCount
        val readsAfterCold = provider.readCount
        val warmResult = resolver.resolve(request)

        assertEquals(coldResult, warmResult)
        assertEquals(scenario.acquisitionCodings.size, acquisitionsAfterCold)
        assertEquals(acquisitionsAfterCold, provider.acquireCount)
        assertEquals(readsAfterCold, provider.readCount)
        assertEquals(provider.acquireCount, provider.closeCount)
        if (scenario.delimiter == null) {
            assertEquals(PlainTextReaderCapability.identity, coldResult.resolvedRead.reader)
        }
        else {
            val config = ConfiguredDelimitedReaderCapability.decode(
                coldResult.resolvedRead.config) as DelimitedReadConfig
            assertEquals(scenario.delimiter, config.dialect.delimiter)
        }

        assertEquals(2, observations.size)
        val cold = observations[0]
        assertEquals(FormatDetectionCacheState.Cold, cold.cacheState)
        assertEquals(scenario.decodedBytes.size, cold.decodedSampleBytes)
        assertEquals(scenario.acquisitionCodings, cold.acquisitionCodings)
        assertTrue(cold.completeLogicalRecordsByCandidate.isNotEmpty())
        assertTrue(cold.completeLogicalRecordsByCandidate.values.all { it in 1..100 })
        assertTrue(cold.elapsedNanoseconds > 0)

        val warm = observations[1]
        assertEquals(FormatDetectionCacheState.WarmBeforeAcquisition, warm.cacheState)
        assertEquals(0, warm.decodedSampleBytes)
        assertTrue(warm.acquisitionCodings.isEmpty())
        assertTrue(warm.completeLogicalRecordsByCandidate.isEmpty())
        assertTrue(warm.elapsedNanoseconds > 0)

        printObservation(scenario.name, "cold", cold)
        printObservation(scenario.name, "warm", warm)
    }


    private fun printObservation(
        scenario: String,
        phase: String,
        observation: AutomaticFormatResolutionObservation
    ) {
        val codings = observation.acquisitionCodings.joinToString(" + ") { it.identity }.ifEmpty { "none" }
        val records = observation.completeLogicalRecordsByCandidate.entries.joinToString("; ") {
            "${it.key}=${it.value}"
        }.ifEmpty { "none" }
        val millis = String.format(Locale.ROOT, "%.3f", observation.elapsedNanoseconds / 1_000_000.0)
        println("| $scenario | $phase | ${observation.decodedSampleBytes} | $codings | $records | $millis | ${observation.cacheState} |")
    }


    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()


    private data class Scenario(
        val name: String,
        val id: String,
        val extension: String,
        val storedBytes: ByteArray,
        val decodedBytes: ByteArray,
        val acquisitionCodings: List<ContentCodingSpec>,
        val delimiter: String?
    )
}
