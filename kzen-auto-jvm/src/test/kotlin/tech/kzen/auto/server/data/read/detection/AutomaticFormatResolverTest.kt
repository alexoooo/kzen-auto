package tech.kzen.auto.server.data.read.detection

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.data.content.DirectDataContext
import tech.kzen.auto.server.data.content.FakeObjectStoreProvider
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.fakeFingerprint
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.server.data.read.text.PlainTextReaderCapability
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class AutomaticFormatResolverTest {
    @Test
    fun semanticMarkdownStaysPlainTextAndContentFindsDelimitedFormats() = runBlocking {
        val context = KzenAutoContext.forTest()

        val markdown = resolve(context, "\n# Heading\n| a | b |".encodeToByteArray(), "README.md", "md")
        assertEquals(PlainTextReaderCapability.identity, markdown.result.resolvedRead.reader)
        assertEquals(FormatResolutionBasis.Extension, markdown.result.detail.basis)

        val semicolon = resolve(context, "name;count\nalice;1\nbob;2".encodeToByteArray(), "records", null)
        val config = ConfiguredDelimitedReaderCapability.decode(
            semicolon.result.resolvedRead.config) as DelimitedReadConfig
        assertEquals(";", config.dialect.delimiter)
        assertEquals(FormatResolutionBasis.Content, semicolon.result.detail.basis)
    }

    @Test
    fun csvFamilyChoosesCommaOrRegionalSemicolonWithoutFalseAmbiguity() = runBlocking {
        val context = KzenAutoContext.forTest()
        val comma = resolve(context, "name,count\nalice,1".encodeToByteArray(), "orders.csv", "csv")
        val regional = resolve(context, "name;count\nalice;1".encodeToByteArray(), "orders.csv", "csv")

        assertEquals(",", config(comma.result).dialect.delimiter)
        assertEquals(";", config(regional.result).dialect.delimiter)
        assertTrue(regional.result.detail.warning.orEmpty().contains("compatible dialect"))
    }

    @Test
    fun mixedCaseGzipSuffixKeepsThePrecedingFormatHintAndWarmCacheReadsNothing() = runBlocking {
        val context = KzenAutoContext.forTest()
        val bytes = gzip("name,count\nalice,1".encodeToByteArray())
        val source = DataSourceId("memory")
        val fingerprint = fakeFingerprint("gzip-v1")
        val provider = FakeObjectStoreProvider(bytes, fingerprint)
        val stack = SequentialContentStack(DataContentProviderLookup(
            tech.kzen.auto.server.data.content.local.LocalDataContentProvider(), mapOf(source to provider)))
        val resolver = AutomaticFormatResolver(
            context.configuredRecordFormatRegistry,
            context.readerCapabilityRegistry,
            DetectionSampleAcquirer(stack))
        val request = FormatResolutionRequest(
            DirectDataContext,
            DataRef(source, "orders.csv.Gz"),
            fingerprint,
            NormalizedFormatHints.of(filenameExtension = "gz"),
            null)

        val first = resolver.resolve(request)
        val readsAfterFirst = provider.readCount
        val second = resolver.resolve(request)

        assertEquals(first, second)
        assertEquals(readsAfterFirst, provider.readCount)
        assertEquals(2, provider.acquireCount)
        assertEquals(FormatSelectionKind.Automatic, first.detail.selection)
        assertEquals(",", config(first).dialect.delimiter)
    }


    @Test
    fun genericTextHintsPreserveOrdinaryAndEmptyTextButAdmitStrongTsv() = runBlocking {
        val context = KzenAutoContext.forTest()

        for ((name, extension, content) in listOf(
            Triple("notes.txt", "txt", "ordinary notes\nsecond line"),
            Triple("service.log", "log", "INFO started\nINFO finished"),
            Triple("empty.txt", "txt", ""))) {
            val result = resolve(context, content.encodeToByteArray(), name, extension).result
            assertEquals(PlainTextReaderCapability.identity, result.resolvedRead.reader, name)
        }

        val tsv = resolve(
            context,
            "name\tcount\nalice\t1\nbob\t2".encodeToByteArray(),
            "export.txt",
            "txt").result
        assertEquals("\t", config(tsv).dialect.delimiter)
        assertEquals(FormatResolutionBasis.Content, tsv.detail.basis)
    }


    @Test
    fun malformedAndUnsafeCharacterInputsNeverSilentlyFallBack() = runBlocking {
        val context = KzenAutoContext.forTest()
        assertFailsWith<FormatDetectionException> {
            resolve(context, "name,value\n\"broken".encodeToByteArray(), "broken.csv", "csv")
        }

        val cp1252 = resolve(
            context,
            "name,city\nalice,Montr".encodeToByteArray() + byteArrayOf(0xe9.toByte()) + "al\n".encodeToByteArray(),
            "legacy.csv",
            "csv").result
        assertEquals("windows-1252", cp1252.detail.resolvedEncoding)
        assertNotNull(cp1252.detail.warning)

        assertFailsWith<FormatDetectionException> {
            resolve(context, byteArrayOf(0xff.toByte()), "opaque", null)
        }
        assertFailsWith<FormatDetectionException> {
            resolve(context, byteArrayOf(0x81.toByte()), "undefined.txt", "txt")
        }
        assertFailsWith<FormatDetectionException> {
            resolve(context, byteArrayOf('a'.code.toByte(), 0, 'b'.code.toByte()), "binary.txt", "txt")
        }
        assertFailsWith<java.nio.charset.MalformedInputException> {
            resolve(
                context,
                byteArrayOf(0xe9.toByte()),
                "strict.txt",
                "txt",
                explicitEncoding = "UTF-8")
        }
        Unit
    }

    private suspend fun resolve(
        context: KzenAutoContext,
        bytes: ByteArray,
        id: String,
        extension: String?,
        explicitEncoding: String? = null
    ): ResolutionFixture {
        val source = DataSourceId("memory")
        val fingerprint = fakeFingerprint(id)
        val provider = FakeObjectStoreProvider(bytes, fingerprint)
        val stack = SequentialContentStack(DataContentProviderLookup(
            tech.kzen.auto.server.data.content.local.LocalDataContentProvider(), mapOf(source to provider)))
        val resolver = AutomaticFormatResolver(
            context.configuredRecordFormatRegistry,
            context.readerCapabilityRegistry,
            DetectionSampleAcquirer(stack))
        val result = resolver.resolve(FormatResolutionRequest(
            DirectDataContext,
            DataRef(source, id),
            fingerprint,
            NormalizedFormatHints.of(filenameExtension = extension),
            explicitEncoding))
        return ResolutionFixture(result, provider)
    }

    private fun config(result: tech.kzen.auto.common.data.format.FormatResolutionResult): DelimitedReadConfig =
        ConfiguredDelimitedReaderCapability.decode(result.resolvedRead.config) as DelimitedReadConfig

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()

    private data class ResolutionFixture(
        val result: tech.kzen.auto.common.data.format.FormatResolutionResult,
        val provider: FakeObjectStoreProvider
    )
}
