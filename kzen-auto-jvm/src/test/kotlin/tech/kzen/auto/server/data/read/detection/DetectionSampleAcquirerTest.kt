package tech.kzen.auto.server.data.read.detection

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.detection.DetectionPolicy
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.server.data.content.DirectDataContext
import tech.kzen.auto.server.data.content.FakeObjectStoreProvider
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.fakeFingerprint
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class DetectionSampleAcquirerTest {
    @Test
    fun plainContentUsesOneAcquisitionAndReportsEndOfInput() = runBlocking {
        val fixture = fixture("alpha\nbeta".encodeToByteArray(), "notes.txt")

        val sample = fixture.acquirer.acquire(fixture.request, policy())

        assertContentEquals("alpha\nbeta".encodeToByteArray(), sample.bytes)
        assertTrue(sample.endOfInput)
        assertEquals(ContentCodingSpec.identity, sample.coding)
        assertEquals(listOf(ContentCodingSpec.identity), sample.acquisitionCodings)
        assertEquals(1, fixture.provider.acquireCount)
        assertEquals(1, fixture.provider.closeCount)
    }

    @Test
    fun gzipContentUsesRawMagicInspectionThenDecodedAcquisition() = runBlocking {
        val decoded = "name,count\nalice,1".encodeToByteArray()
        val fixture = fixture(gzip(decoded), "orders.csv.Gz")

        val sample = fixture.acquirer.acquire(fixture.request, policy())

        assertContentEquals(decoded, sample.bytes)
        assertTrue(sample.endOfInput)
        assertEquals(ContentCodingSpec.gzip, sample.coding)
        assertEquals(
            listOf(ContentCodingSpec.identity, ContentCodingSpec.gzip),
            sample.acquisitionCodings)
        assertEquals(2, fixture.provider.acquireCount)
        assertEquals(2, fixture.provider.closeCount)
    }

    @Test
    fun gzipSuffixWithoutGzipMagicFailsAndClosesTheHandle() = runBlocking {
        val fixture = fixture("not gzip".encodeToByteArray(), "orders.csv.gz")

        assertFailsWith<FormatDetectionException> {
            fixture.acquirer.acquire(fixture.request, policy())
        }
        assertEquals(1, fixture.provider.acquireCount)
        assertEquals(1, fixture.provider.closeCount)
    }


    @Test
    fun decodedSampleStopsAtTheHardByteCeilingAndClosesTheHandle() = runBlocking {
        val fixture = fixture("0123456789abcdef".encodeToByteArray(), "sample.txt")
        val maximum = 8

        val sample = fixture.acquirer.acquire(
            fixture.request,
            policy().copy(maximumDecodedBytes = maximum))

        assertEquals(maximum, sample.bytes.size)
        kotlin.test.assertFalse(sample.endOfInput)
        assertEquals(1, fixture.provider.acquireCount)
        assertEquals(1, fixture.provider.closeCount)
    }

    private fun fixture(bytes: ByteArray, id: String): Fixture {
        val source = DataSourceId("memory")
        val fingerprint = fakeFingerprint(id)
        val provider = FakeObjectStoreProvider(bytes, fingerprint)
        val stack = SequentialContentStack(DataContentProviderLookup(
            LocalDataContentProvider(), mapOf(source to provider)))
        return Fixture(
            DetectionSampleAcquirer(stack),
            FormatResolutionRequest(
                DirectDataContext,
                DataRef(source, id),
                fingerprint,
                NormalizedFormatHints.of(filenameExtension = id.substringAfterLast('.')),
                null),
            provider)
    }

    private fun policy(): DetectionPolicy = DetectionPolicy.default(emptyList()).copy(
        maximumDecodedBytes = 1024)

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()

    private data class Fixture(
        val acquirer: DetectionSampleAcquirer,
        val request: FormatResolutionRequest,
        val provider: FakeObjectStoreProvider
    )
}
