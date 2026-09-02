package tech.kzen.auto.server.data.content

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.DelimitedDialectSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.common.data.read.TypedDecodePolicy
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.FilePath
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReader
import tech.kzen.auto.server.data.read.delimited.DelimitedReadContext
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.policy.ContentReadPolicy
import tech.kzen.auto.server.data.content.policy.ContentTimeoutException
import tech.kzen.auto.server.data.content.provider.DataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


class FakeObjectStoreConformanceTest {
    companion object {
        private val characters = CharacterDecodingSpec("UTF-8", "permit", "report", "report")
        private val policy = ContentReadPolicy(1024 * 1024, 10.seconds, 100)
    }


    @Test
    fun configuredReaderValuesAndContractMatchLocalPlainAndGzip() = runBlocking {
        val text = "name,count\nalpha,1\nbeta,2\n"
        val compressedBytes = gzip(text.encodeToByteArray())
        val plainFile = Files.createTempFile("configured-local-plain", ".data")
        val gzipFile = Files.createTempFile("configured-local-gzip", ".data")
        try {
            Files.writeString(plainFile, text)
            Files.write(gzipFile, compressedBytes)
            val configured = configuredSchema()

            val localPlain = readConfigured(
                localRef(plainFile), null, null, ContentCodingSpec.identity, configured)
            val localGzip = readConfigured(
                localRef(gzipFile), null, null, ContentCodingSpec.gzip, configured)
            val fakePlainProvider = FakeObjectStoreProvider(
                text.encodeToByteArray(), fakeFingerprint("plain-reader-v1"),
                expectedId = "bucket-a/data/plain-key")
            val fakeGzipProvider = FakeObjectStoreProvider(
                compressedBytes, fakeFingerprint("gzip-reader-v1"),
                expectedId = "bucket-b/data/compressed-key")
            val fakePlain = readConfigured(
                ref("fake-reader-plain", "bucket-a/data/plain-key"),
                "fake-reader-plain", fakePlainProvider, ContentCodingSpec.identity, configured)
            val fakeGzip = readConfigured(
                ref("fake-reader-gzip", "bucket-b/data/compressed-key"),
                "fake-reader-gzip", fakeGzipProvider, ContentCodingSpec.gzip, configured)

            assertEquals(localPlain, localGzip)
            assertEquals(localPlain, fakePlain)
            assertEquals(localPlain, fakeGzip)
            assertEquals(listOf(listOf("alpha", "1"), listOf("beta", "2")), localPlain.rows)
            assertEquals(1, fakePlainProvider.closeCount)
            assertEquals(1, fakeGzipProvider.closeCount)
        }
        finally {
            Files.deleteIfExists(plainFile)
            Files.deleteIfExists(gzipFile)
        }
    }


    @Test
    fun opaquePlainAndGzipObjectsConvergeWithoutProviderCodingKnowledge() = runBlocking {
        val text = "name,value\nalpha,1\n"
        val plainProvider = FakeObjectStoreProvider(
            text.encodeToByteArray(), fakeFingerprint("plain-v1"),
            expectedId = "bucket-a/reports/opaque-42")
        val gzipProvider = FakeObjectStoreProvider(
            gzip(text.encodeToByteArray()), fakeFingerprint("gzip-v1"),
            expectedId = "bucket-b/archive/key-without-extension")

        val plain = stack("plain", plainProvider).openCharacters(
            DirectDataContext, ref("plain", "bucket-a/reports/opaque-42"), null,
            listOf(ContentCodingSpec.identity), characters, policy)
        val compressed = stack("gzip", gzipProvider).openCharacters(
            DirectDataContext, ref("gzip", "bucket-b/archive/key-without-extension"), null,
            listOf(ContentCodingSpec.gzip), characters, policy)

        assertEquals(text, plain.readAllText())
        assertEquals(text, compressed.readAllText())
        assertEquals(1, plainProvider.closeCount)
        assertEquals(1, gzipProvider.closeCount)
    }


    @Test
    fun operationalLimitsRemainAboveOpaqueProviderDispatch() = runBlocking {
        val compressed = gzip("value".repeat(100).encodeToByteArray())
        val expansionProvider = FakeObjectStoreProvider(
            compressed, fakeFingerprint("expanded-v1"), expectedId = "opaque-compressed")
        val expansionLimit = ContentReadPolicy(20, 10.seconds, 100)
        val content = stack("expanded", expansionProvider).openCharacters(
            DirectDataContext, ref("expanded", "opaque-compressed"), null,
            listOf(ContentCodingSpec.gzip), characters, expansionLimit)
        val reader = ConfiguredDelimitedReader.open(
            content, oneTextFieldConfig(), ReadOperationalPolicy(), DelimitedReadContext("opaque-compressed"))
        assertFailsWith<ContentCodingException> { reader.use { it.read() } }
        assertEquals(1, expansionProvider.closeCount)

        val timeoutProvider = FakeObjectStoreProvider(
            "value".encodeToByteArray(), fakeFingerprint("timeout-v1"), acquisitionDelayMillis = 25,
            expectedId = "opaque-slow")
        val timeoutPolicy = ContentReadPolicy(1024, 5.milliseconds, 100)
        assertFailsWith<ContentTimeoutException> {
            stack("timeout", timeoutProvider).openCharacters(
                DirectDataContext, ref("timeout", "opaque-slow"), null,
                listOf(ContentCodingSpec.identity), characters, timeoutPolicy)
        }
        assertEquals(1, timeoutProvider.closeCount)

        val cancellationProvider = FakeObjectStoreProvider(
            "value".encodeToByteArray(), fakeFingerprint("cancel-v1"), cancelOnRead = true,
            expectedId = "opaque-cancelled")
        val cancellationContent = stack("cancel", cancellationProvider).openCharacters(
            DirectDataContext, ref("cancel", "opaque-cancelled"), null,
            listOf(ContentCodingSpec.identity), characters, policy)
        val cancellationReader = ConfiguredDelimitedReader.open(
            cancellationContent, oneTextFieldConfig(), ReadOperationalPolicy(),
            DelimitedReadContext("opaque-cancelled"))
        assertFailsWith<java.util.concurrent.CancellationException> {
            cancellationReader.use { it.read() }
        }
        assertEquals(1, cancellationProvider.closeCount)
    }


    @Test
    fun staleObjectVersionAndCapabilityMismatchFailBeforeReading() = runBlocking {
        val staleProvider = FakeObjectStoreProvider(
            "value".encodeToByteArray(), fakeFingerprint("observed-v2"),
            expectedId = "opaque-versioned")
        assertFailsWith<ContentSourceException> {
            stack("stale", staleProvider).openCharacters(
                DirectDataContext, ref("stale", "opaque-versioned"), fakeFingerprint("expected-v1"),
                listOf(ContentCodingSpec.identity), characters, policy)
        }
        assertEquals(0, staleProvider.readCount)
        assertEquals(1, staleProvider.closeCount)

        val unavailableProvider = FakeObjectStoreProvider(
            byteArrayOf(), fakeFingerprint("capability-v1"), emptySet(),
            expectedId = "opaque-native-rows")
        val failure = assertFailsWith<ContentSourceException> {
            stack("capability", unavailableProvider).openCharacters(
                DirectDataContext, ref("capability", "opaque-native-rows"), null,
                listOf(ContentCodingSpec.identity), characters, policy)
        }
        assertContains(failure.message.orEmpty(), "sequential-bytes")
        assertContains(failure.message.orEmpty(), "available capabilities: none")
        assertEquals(0, unavailableProvider.acquireCount)
    }


    private suspend fun readConfigured(
        ref: DataRef,
        providerId: String?,
        provider: DataContentProvider?,
        coding: ContentCodingSpec,
        config: DelimitedReadConfig
    ): ConfiguredResult {
        val providers = if (providerId == null) {
            emptyMap()
        }
        else {
            mapOf(DataSourceId(providerId) to requireNotNull(provider))
        }
        val content = SequentialContentStack(
            DataContentProviderLookup(LocalDataContentProvider(), providers))
            .openCharacters(
                DirectDataContext, ref,
                null,
                listOf(coding), config.characters, policy)
        return ConfiguredDelimitedReader.open(
            content, config, ReadOperationalPolicy(), DelimitedReadContext(ref.display())).use { reader ->
            val rows = mutableListOf<List<String>>()
            while (true) {
                val record = reader.read() ?: break
                rows.add(record.backing.toList())
            }
            ConfiguredResult(reader.contract, rows)
        }
    }


    private fun configuredSchema(): DelimitedReadConfig {
        val contract = DataContract(DataType.Record(listOf(
            DataField(FieldId("name", 0), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId("count", 0), DataType.Scalar(ScalarKind.Integer(32))))))
        return DelimitedReadConfig(
            RecordFramingSpec("lf"),
            DelimitedDialectSpec(",", "\"", "double-quote", "empty", "none"),
            HeaderReadSpec("present", "exact-name"),
            characters,
            contract,
            TypedDecodePolicy(null, "fail-part", emptyList()))
    }


    private fun oneTextFieldConfig(): DelimitedReadConfig {
        val contract = DataContract(DataType.Record(listOf(
            DataField(FieldId("value", 0), DataType.Scalar(ScalarKind.Text)))))
        return DelimitedReadConfig(
            RecordFramingSpec("lf"),
            DelimitedDialectSpec(",", "\"", "double-quote", "empty", "none"),
            HeaderReadSpec("absent", "exact-name"),
            characters,
            contract,
            TypedDecodePolicy(null, "fail-part", emptyList()))
    }


    private fun stack(providerId: String, provider: DataContentProvider): SequentialContentStack {
        return SequentialContentStack(
            DataContentProviderLookup(LocalDataContentProvider(), mapOf(DataSourceId(providerId) to provider)))
    }


    private fun ref(providerId: String, id: String): DataRef {
        return DataRef(DataSourceId(providerId), id)
    }


    private fun localRef(path: java.nio.file.Path): DataRef {
        val location = DataLocation.ofFile(FilePath.of(path.toAbsolutePath().normalize().toString()))
        return DataRef(null, location.asString())
    }


    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }


    private data class ConfiguredResult(
        val contract: DataContract,
        val rows: List<List<String>>
    )
}
