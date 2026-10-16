package tech.kzen.auto.server.data.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.data.read.DelimitedDialectSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.read.TypedDecodePolicy
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.FilePath
import tech.kzen.auto.server.data.ConfiguredDataOpener
import tech.kzen.auto.server.data.SchemaCache
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import com.linkedin.migz.MiGzOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class SequentialContentStackTest {
    companion object {
        private val utf8 = CharacterDecodingSpec("UTF-8", "permit", "report", "report")
    }

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()


    @Test
    fun localPlainAndGzipProduceTheSameCharacters() = runBlocking {
        val text = "name,value\nalpha,1\n"
        val plain = Files.createTempFile("content-stack", ".txt")
        val gzip = Files.createTempFile("content-stack", ".bin")
        try {
            Files.writeString(plain, text)
            Files.write(gzip, gzip(text.encodeToByteArray()))
            val stack = localStack()

            val plainCharacters = stack.openCharacters(
                DirectDataContext, localRef(plain), null, listOf(ContentCodingSpec.identity), utf8)
            val gzipCharacters = stack.openCharacters(
                DirectDataContext, localRef(gzip), null, listOf(ContentCodingSpec.gzip), utf8)

            assertEquals(text, plainCharacters.readAllText())
            assertEquals(text, gzipCharacters.readAllText())
        }
        finally {
            Files.deleteIfExists(plain)
            Files.deleteIfExists(gzip)
        }
    }


    @Test
    fun sourceBytesCountTheStoredContentBeneathTheCoding() = runBlocking {
        val text = "name,value\n" + "alpha,1\n".repeat(1000)
        val stored = gzip(text.encodeToByteArray())
        val file = Files.createTempFile("content-stack", ".gz")
        try {
            Files.write(file, stored)
            val input = localStack().openBytes(
                DirectDataContext, localRef(file), null, listOf(ContentCodingSpec.gzip),
                ContentCapabilityIdentity.sequentialBytes)
            input.use {
                assertEquals(0, it.sourceBytesRead)
                val buffer = ByteArray(64)
                var expanded = 0L
                while (true) {
                    val count = it.read(buffer, 0, buffer.size)
                    if (count == -1) break
                    expanded += count
                }
                assertEquals(text.length.toLong(), expanded)
                assertEquals(stored.size.toLong(), it.sourceBytesRead)
            }
        }
        finally {
            Files.deleteIfExists(file)
        }
    }


    @Test
    fun localFingerprintMutationFailsBeforeCharactersAreReturned() = runBlocking {
        val file = Files.createTempFile("content-stack-fingerprint", ".txt")
        try {
            Files.writeString(file, "before")
            val ref = localRef(file)
            val expected = DataContentFingerprint.localOrNull(ref)
            Files.writeString(file, "after mutation")

            val failure = assertFailsWith<ContentSourceException> {
                localStack().openCharacters(
                    DirectDataContext, ref, expected, listOf(ContentCodingSpec.identity), utf8)
            }
            assertContains(failure.message.orEmpty(), "Fingerprint changed")
        }
        finally {
            Files.deleteIfExists(file)
        }
    }


    @Test
    fun opaqueProviderRoutesByDurableSourceAndChecksFingerprint() = runBlocking {
        val source = DataSourceId("memory")
        val ref = DataRef(source, "opaque-object-7")
        val provider = FakeObjectStoreProvider("hello".encodeToByteArray(), fakeFingerprint("v2"))
        val stack = SequentialContentStack(
            DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to provider)))

        assertFailsWith<ContentSourceException> {
            stack.openCharacters(
                DirectDataContext, ref, fakeFingerprint("v1"),
                listOf(ContentCodingSpec.identity), utf8, "payload")
        }
        assertEquals(1, provider.closeCount)
        assertEquals(0, provider.readCount)
    }


    @Test
    fun unknownAndUnsupportedProvidersFailBeforeAcquisition() = runBlocking {
        val unknownRef = DataRef(DataSourceId("missing"), "opaque")
        val emptyLookup = SequentialContentStack(DataContentProviderLookup(LocalDataContentProvider(), emptyMap()))
        assertFailsWith<ContentSourceException> {
            emptyLookup.openCharacters(
                DirectDataContext, unknownRef, null, listOf(ContentCodingSpec.identity), utf8)
        }

        val source = DataSourceId("unsupported")
        val provider = FakeObjectStoreProvider(byteArrayOf(), fakeFingerprint("v1"), emptySet())
        val stack = SequentialContentStack(DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to provider)))
        assertFailsWith<ContentSourceException> {
            stack.openCharacters(
                DirectDataContext, DataRef(source, "opaque"), null,
                listOf(ContentCodingSpec.identity), utf8)
        }
        assertEquals(0, provider.acquireCount)
    }


    @Test
    fun builtInCodingConfigIsRejectedBeforeTheByteStreamIsWrapped() = runBlocking {
        val source = DataSourceId("memory")
        for (builtIn in listOf(ContentCodingSpec.identity, ContentCodingSpec.gzip)) {
            val provider = FakeObjectStoreProvider("hello".encodeToByteArray(), fakeFingerprint("v1"))
            val stack = SequentialContentStack(
                DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to provider)))
            val configured = ContentCodingSpec(
                builtIn.identity,
                MapExecutionValue(mapOf("ignored" to TextExecutionValue("value"))))

            val failure = assertFailsWith<ContentCodingException> {
                stack.openCharacters(
                    DirectDataContext, DataRef(source, "opaque"), null,
                    listOf(configured), utf8)
            }

            assertContains(failure.message.orEmpty(), "empty configuration map")
            assertEquals(1, provider.acquireCount)
            assertEquals(0, provider.readCount)
            assertEquals(1, provider.closeCount)
        }
    }


    @Test
    fun invalidCharacterSettingsFailResolutionBeforeContentAcquisition() = runBlocking {
        val source = DataSourceId("memory")
        val provider = FakeObjectStoreProvider("hello".encodeToByteArray(), fakeFingerprint("v1"))
        val stack = SequentialContentStack(
            DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to provider)))
        val cacheDirectory = Files.createTempDirectory("invalid-character-config")
        try {
            val opener = ConfiguredDataOpener(
                SchemaCache(WorkUtils(cacheDirectory)), contentStack = stack)
            val invalidSettings = listOf(
                CharacterDecodingSpec("auto", "detect", "report", "report"),
                CharacterDecodingSpec("missing-charset", "permit", "report", "report"),
                CharacterDecodingSpec("UTF-16", "require", "report", "report"),
                CharacterDecodingSpec("UTF-8", "unknown", "report", "report"),
                CharacterDecodingSpec("UTF-8", "permit", "unknown", "report"),
                CharacterDecodingSpec("UTF-8", "permit", "report", "unknown"))

            for (characters in invalidSettings) {
                val config = configuredReadConfig(characters)
                val part = DataPart(
                    DataRole.main,
                    DataRef(source, "opaque"),
                    null,
                    ResolvedReadSpec(
                        ConfiguredDelimitedReaderCapability.identity,
                        listOf(ContentCodingSpec.identity),
                        config.asExecutionValue()))

                assertFailsWith<IllegalArgumentException> {
                    opener.open(DirectDataContext, part)
                }
            }
            assertEquals(0, provider.acquireCount)
            assertEquals(0, provider.readCount)
            assertEquals(0, provider.closeCount)
        }
        finally {
            Files.deleteIfExists(cacheDirectory)
        }
    }


    @Test
    fun delimiterEqualToQuoteFailsBeforeContentAcquisition() = runBlocking {
        val source = DataSourceId("memory")
        val provider = FakeObjectStoreProvider("value".encodeToByteArray(), fakeFingerprint("v1"))
        val stack = SequentialContentStack(
            DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to provider)))
        val config = configuredReadConfig(utf8).let { configured ->
            configured.copy(dialect = configured.dialect.copy(delimiter = "|", quote = "|"))
        }
        val part = DataPart(
            DataRole.main,
            DataRef(source, "opaque"),
            null,
            ResolvedReadSpec(
                ConfiguredDelimitedReaderCapability.identity,
                listOf(ContentCodingSpec.identity),
                config.asExecutionValue()))

        assertFailsWith<IllegalArgumentException> {
            ConfiguredDataOpener(
                SchemaCache(WorkUtils(temp.newFolder("invalid-delimiter-quote").toPath())),
                contentStack = stack
            ).open(DirectDataContext, part)
        }
        assertEquals(0, provider.acquireCount)
        assertEquals(0, provider.readCount)
        assertEquals(0, provider.closeCount)
    }


    @Test
    fun configuredCharacterSettingsCanonicalizeBeforeSnapshotEncoding() {
        val configured = configuredReadConfig(
            CharacterDecodingSpec("utf8", "PERMIT", "REPORT", "REPLACE"))

        val canonical = ConfiguredDelimitedReaderCapability.canonicalize(configured) as DelimitedReadConfig

        assertEquals("UTF-8", canonical.characters.charset)
        assertEquals("permit", canonical.characters.bom)
        assertEquals("report", canonical.characters.malformed)
        assertEquals("replace", canonical.characters.unmappable)
        assertEquals(
            canonical.asExecutionValue(),
            ConfiguredDelimitedReaderCapability.encode(configured))
    }


    @Test
    fun inspectionReadsOnlyWhatTheReaderNeedsToStateTheShape() = runBlocking {
        val source = DataSourceId("memory")
        val bytes = ("a\n" + "b\n".repeat(100_000)).encodeToByteArray()
        val provider = FakeObjectStoreProvider(bytes, fakeFingerprint("v1"))
        val stack = SequentialContentStack(
            DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to provider)))
        val config = configuredReadConfig(utf8, inferLabels = true)
        val part = DataPart(
            DataRole.main,
            DataRef(source, "opaque"),
            null,
            ResolvedReadSpec(
                ConfiguredDelimitedReaderCapability.identity,
                listOf(ContentCodingSpec.identity),
                ConfiguredDelimitedReaderCapability.encode(config)))

        val shape = ConfiguredDataOpener(
            SchemaCache(WorkUtils(temp.newFolder().toPath())),
            contentStack = stack
        ).inspectShape(DirectDataContext, part)
        val coverage = (shape.stability as ShapeStability.Provisional).coverage

        assertEquals(1, coverage.observedItems)
        assertEquals(1, provider.closeCount)
    }


    @Test
    fun gzipCorruptionAndTrailingBytesAreDeterministic() = runBlocking {
        val valid = gzip("compressible".repeat(100).encodeToByteArray())
        val truncated = valid.copyOf(valid.size - 3)
        val trailing = valid + byteArrayOf(1)

        assertFailsWith<ContentCodingException> { memoryCharacters(truncated, ContentCodingSpec.gzip).readAllText() }
        val trailingFailure = assertFailsWith<ContentCodingException> {
            memoryCharacters(trailing, ContentCodingSpec.gzip).readAllText()
        }
        assertContains(trailingFailure.message.orEmpty(), "after a valid .gz stream")
        val memberThenTrailing = assertFailsWith<ContentCodingException> {
            memoryCharacters(valid + valid + byteArrayOf(1), ContentCodingSpec.gzip).readAllText()
        }
        assertContains(memberThenTrailing.message.orEmpty(), "after a valid .gz stream")
        Unit
    }


    @Test
    fun gzipOfManyMembersReadsEveryMemberAndChecksEach() = runBlocking {
        val text = (0 until 20_000).joinToString("\n") { "row $it" }
        val bytes = text.encodeToByteArray()

        // Write's and Report's MiGz encoder: a member per block, members crossing the reader's input buffer.
        val raw = ByteArrayOutputStream()
        MiGzOutputStream(raw as OutputStream, 3, 1000).use { it.write(bytes) }
        val migz = raw.toByteArray()
        assertEquals(text, memoryCharacters(migz, ContentCodingSpec.gzip).readAllText())

        val half = bytes.size / 2
        val concatenated = gzip(bytes.copyOf(half)) + gzip(bytes.copyOfRange(half, bytes.size))
        assertEquals(text, memoryCharacters(concatenated, ContentCodingSpec.gzip).readAllText())

        val second = gzip(bytes.copyOfRange(half, bytes.size))
        second[second.size - 8] = (second[second.size - 8] + 1).toByte()
        val corruptSecond = assertFailsWith<ContentCodingException> {
            memoryCharacters(gzip(bytes.copyOf(half)) + second, ContentCodingSpec.gzip).readAllText()
        }
        assertContains(corruptSecond.message.orEmpty(), "CRC32")
    }


    @Test
    fun aLargeExpansionReadsToTheEnd() = runBlocking {
        val text = "compressible".repeat(1024 * 1024)
        val expanded = memoryCharacters(gzip(text.encodeToByteArray()), ContentCodingSpec.gzip)
        assertEquals(text.length, expanded.readAllText().length)
    }


    @Test
    fun bomAndMalformedPoliciesAreExplicit() = runBlocking {
        val utf8Bom = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + "value".encodeToByteArray()
        assertEquals("value", memoryCharacters(utf8Bom, characters = utf8).readAllText())
        assertFailsWith<CharacterDecodingException> {
            memoryCharacters(utf8Bom, characters = CharacterDecodingSpec("UTF-8", "forbid", "report", "report"))
        }

        val utf16 = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + "value".toByteArray(Charsets.UTF_16LE)
        val detected = memoryCharacters(utf16, characters = CharacterDecodingSpec("auto", "detect", "report", "report"))
        assertEquals("UTF-16LE", detected.resolvedCharsetName)
        assertEquals("value", detected.readAllText())
        assertFailsWith<CharacterDecodingException> {
            memoryCharacters(utf16, characters = CharacterDecodingSpec("UTF-16BE", "permit", "report", "report"))
        }
        assertFailsWith<CharacterDecodingException> {
            memoryCharacters("value".encodeToByteArray(), characters = CharacterDecodingSpec("UTF-16", "permit", "report", "report"))
        }

        val malformed = byteArrayOf(0x61, 0xc3.toByte(), 0x28)
        val reportFailure = assertFailsWith<CharacterDecodingException> {
            memoryCharacters(malformed).readAllText()
        }
        assertEquals(1, reportFailure.byteOffset)
        val replacement = memoryCharacters(
            malformed,
            characters = CharacterDecodingSpec("UTF-8", "permit", "replace", "replace"))
        assertEquals("a\ufffd(", replacement.readAllText())
    }


    @Test
    fun cancellationDuringAcquisitionAndPullClosesExactlyOnce() = runBlocking {
        val source = DataSourceId("memory")
        val acquisitionProvider = FakeObjectStoreProvider(
            "hello".encodeToByteArray(), fakeFingerprint("v1"), cancelAfterAcquisition = true)
        val acquisitionStack = SequentialContentStack(
            DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to acquisitionProvider)))
        assertFailsWith<CancellationException> {
            acquisitionStack.openCharacters(
                DirectDataContext, DataRef(source, "opaque"), null,
                listOf(ContentCodingSpec.identity), utf8)
        }
        assertEquals(1, acquisitionProvider.closeCount)

        val pullProvider = FakeObjectStoreProvider(
            "hello".encodeToByteArray(), fakeFingerprint("v1"), cancelOnRead = true)
        val pullStack = SequentialContentStack(
            DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to pullProvider)))
        val characters = pullStack.openCharacters(
            DirectDataContext, DataRef(source, "opaque"), null,
            listOf(ContentCodingSpec.identity), utf8)
        assertFailsWith<CancellationException> { characters.readAllText() }
        assertEquals(1, pullProvider.closeCount)
        characters.close()
        assertEquals(1, pullProvider.closeCount)
    }


    private suspend fun memoryCharacters(
        bytes: ByteArray,
        coding: ContentCodingSpec = ContentCodingSpec.identity,
        characters: CharacterDecodingSpec = utf8
    ): SequentialCharacterContent {
        val source = DataSourceId("memory")
        val provider = FakeObjectStoreProvider(bytes, fakeFingerprint("v1"))
        val stack = SequentialContentStack(
            DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to provider)))
        return stack.openCharacters(
            DirectDataContext, DataRef(source, "opaque"), null,
            listOf(coding), characters)
    }


    private fun localStack(): SequentialContentStack {
        return SequentialContentStack(DataContentProviderLookup(LocalDataContentProvider(), emptyMap()))
    }


    private fun configuredReadConfig(
        characters: CharacterDecodingSpec,
        inferLabels: Boolean = false
    ): DelimitedReadConfig =
        DelimitedReadConfig(
            RecordFramingSpec("lf"),
            DelimitedDialectSpec(",", "\"", "double-quote", "empty", "unquoted"),
            HeaderReadSpec(if (inferLabels) "infer-labels" else "absent", "exact-name"),
            characters,
            if (inferLabels) null else DataContract(DataType.Record(listOf(
                DataField(FieldId("value", 0), DataType.Scalar(ScalarKind.Text))))),
            TypedDecodePolicy(null, "fail-part", emptyList()))


    private fun localRef(path: java.nio.file.Path): DataRef {
        val attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java)
        val location = DataLocation.ofFile(FilePath.of(path.toAbsolutePath().normalize().toString()))
        return DataRef(
            null,
            location.asString(),
            linkedMapOf(
                DataRef.sizeKey to attributes.size().toString(),
                DataRef.modifiedKey to kotlin.time.Instant
                    .fromEpochMilliseconds(attributes.lastModifiedTime().toMillis()).toString()))
    }


    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }
}


internal fun SequentialCharacterContent.readAllText(): String {
    val builder = StringBuilder()
    val buffer = CharArray(17)
    while (true) {
        val count = read(buffer)
        if (count == -1) return builder.toString()
        assertTrue(count > 0)
        builder.append(buffer, 0, count)
    }
}
