package tech.kzen.auto.server.data.read

import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.ConfiguredDataOpener
import tech.kzen.auto.server.data.SchemaCache
import tech.kzen.auto.server.data.content.DirectDataContext
import tech.kzen.auto.server.data.content.FakeObjectStoreProvider
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.fakeFingerprint
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.plugin.api.data.ReaderCapability
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeCapability
import tech.kzen.auto.plugin.api.data.ReaderProbeRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeResult
import tech.kzen.auto.plugin.api.data.FormatAuthoringCapability
import tech.kzen.auto.common.data.format.FormatMaterializationRequest
import tech.kzen.auto.common.data.format.FormatMaterializationResult
import tech.kzen.auto.server.data.content.SequentialByteContent
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import tech.kzen.auto.server.data.content.provider.DataContentDescriptor
import tech.kzen.auto.server.data.content.provider.DataContentHandle
import tech.kzen.auto.server.data.content.provider.DataContentProvider
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.auto.server.util.WorkUtils
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertContains


class ReaderCapabilityRegistryTest {
    @Test
    fun resolvesCanonicalConfigWithoutReaderNameBranch() {
        val registry = ReaderCapabilityRegistry.withConfiguredReaders()
        val spec = ConfiguredDelimitedTestFormats.csv().resolvedRead(DataRef(null, "input.csv"))

        val config = registry.decodeValidateCanonicalize(spec)

        assertEquals(spec.config, ConfiguredDelimitedReaderCapability.encode(config))
        assertEquals(ContentCapabilityIdentity.sequentialBytes,
            ConfiguredDelimitedReaderCapability.requiredContent(config))
    }

    @Test
    fun duplicateAndUnknownIdentitiesFailEarly() {
        assertFailsWith<IllegalStateException> {
            ReaderCapabilityRegistry(listOf(
                ConfiguredDelimitedReaderCapability,
                ConfiguredDelimitedReaderCapability))
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderCapabilityRegistry(emptyList()).resolve(
                ReaderCapabilityIdentity("test", "unknown", "1"))
        }
    }


    @Test
    fun optionalProbeIsIndexedByCompatibilityWhileOrdinaryReaderRemainsRunnable() {
        val probeReader = ProbeReader("probe", "probe-v1")
        val ordinaryReader = object: ReaderCapability by TestServiceReaderCapability() {}
        val registry = ReaderCapabilityRegistry(listOf(probeReader, ordinaryReader))

        assertEquals(probeReader, registry.probeFor(probeReader.identity))
        assertEquals(null, registry.probeFor(ordinaryReader.identity))
        assertEquals(setOf("probe-v1"), registry.probeCompatibilityIdentities())
        assertEquals(ordinaryReader, registry.resolve(ordinaryReader.identity))
    }


    @Test
    fun duplicateProbeCompatibilityFailsEvenWhenReaderIdentitiesDiffer() {
        assertFailsWith<IllegalStateException> {
            ReaderCapabilityRegistry(listOf(
                ProbeReader("first", "shared-probe-v1"),
                ProbeReader("second", "shared-probe-v1")))
        }
    }


    @Test
    fun duplicateAuthoringIdentityFailsEvenWhenReadersDiffer() {
        assertFailsWith<IllegalStateException> {
            ReaderCapabilityRegistry(listOf(
                AuthoringReader("first", "reader-first"),
                AuthoringReader("second", "reader-second")))
        }
    }

    @Test
    fun serviceLoadedPluginRoundTripsOpensAndInspectsProviderNeutralBytes() = runBlocking {
        val registry = ReaderCapabilityRegistry.withConfiguredReaders(
            Thread.currentThread().contextClassLoader)
        val capability = assertIs<TestServiceReaderCapability>(
            registry.resolve(TestServiceReaderCapability.serviceIdentity))
        val canonicalData = MapExecutionValue(mapOf("prefix" to TextExecutionValue("plugin: ")))
        val spec = ResolvedReadSpec(
            capability.identity,
            listOf(ContentCodingSpec.identity),
            canonicalData)

        val config = registry.decodeValidateCanonicalize(spec)

        assertEquals(canonicalData, capability.encode(config))
        assertEquals(spec.configDigest, capability.encode(config).digest())

        val source = DataSourceId("plugin-object-store")
        val fingerprint = fakeFingerprint("plugin-v1")
        val provider = FakeObjectStoreProvider(
            "payload".encodeToByteArray(), fingerprint, expectedId = "opaque-key")
        val opener = ConfiguredDataOpener(
            SchemaCache(WorkUtils.temporary("plugin-reader-spi")),
            registry,
            SequentialContentStack(DataContentProviderLookup(
                LocalDataContentProvider(), mapOf(source to provider))))
        val part = DataPart(
            DataRole.main,
            DataRef(source, "opaque-key"),
            fingerprint,
            spec)

        opener.open(DirectDataContext, part).use { cursor ->
            val value = cursor.next()
            assertEquals("plugin: payload", value.access.readText(value.root))
        }
        assertEquals(
            ShapeProvenance.Declared,
            opener.inspectShape(DirectDataContext, part).provenance)
        assertEquals(2, provider.acquireCount)
        assertEquals(2, provider.closeCount)
    }


    @Test
    fun compositionRootClosesAcquiredBytesWhenPluginOpenFails() = runBlocking {
        val config = MapExecutionValue(mapOf("prefix" to TextExecutionValue("")))
        val source = DataSourceId("failing-plugin-store")
        val fingerprint = fakeFingerprint("failing-plugin-v1")
        val provider = FakeObjectStoreProvider(
            "unread".encodeToByteArray(), fingerprint, expectedId = "opaque-key")
        val opener = ConfiguredDataOpener(
            SchemaCache(WorkUtils.temporary("failing-plugin-reader-spi")),
            ReaderCapabilityRegistry(listOf(OpeningFailureCapability)),
            SequentialContentStack(DataContentProviderLookup(
                LocalDataContentProvider(), mapOf(source to provider))))
        val part = DataPart(
            DataRole.main,
            DataRef(source, "opaque-key"),
            fingerprint,
            ResolvedReadSpec(
                OpeningFailureCapability.identity,
                listOf(ContentCodingSpec.identity),
                config))

        assertFailsWith<IllegalStateException> {
            opener.open(DirectDataContext, part)
        }
        assertEquals(1, provider.acquireCount)
        assertEquals(1, provider.closeCount)
    }


    @Test
    fun inspectionRetainsPluginFailureAndSuppressesCloseFailure() = runBlocking {
        val config = MapExecutionValue(mapOf("prefix" to TextExecutionValue("")))
        val source = DataSourceId("inspection-failure-store")
        val fingerprint = fakeFingerprint("inspection-failure-v1")
        val provider = CloseFailureProvider(fingerprint)
        val opener = ConfiguredDataOpener(
            SchemaCache(WorkUtils.temporary("inspection-failure-reader-spi")),
            ReaderCapabilityRegistry(listOf(InspectionFailureCapability)),
            SequentialContentStack(DataContentProviderLookup(
                LocalDataContentProvider(), mapOf(source to provider))))
        val part = DataPart(
            DataRole.main,
            DataRef(source, "opaque-key"),
            fingerprint,
            ResolvedReadSpec(
                InspectionFailureCapability.identity,
                listOf(ContentCodingSpec.identity),
                config))

        val failure = assertFailsWith<IllegalArgumentException> {
            opener.inspectShape(DirectDataContext, part)
        }
        assertContains(failure.message.orEmpty(), "plugin inspection failed")
        assertEquals(1, failure.suppressed.size)
        assertContains(failure.suppressed.single().message.orEmpty(), "byte close failed")
        assertEquals(1, provider.closeCount)
    }


    @Test
    fun returnedPluginCursorFailureClosesAcquiredBytesExactlyOnce() = runBlocking {
        val config = MapExecutionValue(mapOf("prefix" to TextExecutionValue("")))
        val source = DataSourceId("pull-failure-store")
        val fingerprint = fakeFingerprint("pull-failure-v1")
        val provider = FakeObjectStoreProvider(
            "payload-that-requires-more-than-one-read".encodeToByteArray(),
            fingerprint,
            cancelOnRead = true,
            expectedId = "opaque-key")
        val opener = ConfiguredDataOpener(
            SchemaCache(WorkUtils.temporary("pull-failure-reader-spi")),
            ReaderCapabilityRegistry(listOf(TestServiceReaderCapability())),
            SequentialContentStack(DataContentProviderLookup(
                LocalDataContentProvider(), mapOf(source to provider))))
        val part = DataPart(
            DataRole.main,
            DataRef(source, "opaque-key"),
            fingerprint,
            ResolvedReadSpec(
                TestServiceReaderCapability.serviceIdentity,
                listOf(ContentCodingSpec.identity),
                config))

        val cursor = opener.open(DirectDataContext, part)
        assertFailsWith<java.util.concurrent.CancellationException> { cursor.hasNext() }
        assertEquals(1, provider.closeCount)
        cursor.close()
        assertEquals(1, provider.closeCount)
    }


    private object OpeningFailureCapability:
        ReaderCapability by TestServiceReaderCapability()
    {
        override val identity = ReaderCapabilityIdentity(
            "third.party.test", "opening-failure", "1")

        override suspend fun open(request: ReaderOpenRequest): DataCursor =
            error("plugin open failed")
    }


    private object InspectionFailureCapability:
        ReaderCapability by TestServiceReaderCapability()
    {
        override val identity = ReaderCapabilityIdentity(
            "third.party.test", "inspection-failure", "1")

        override suspend fun inspect(request: ReaderInspectionRequest): DataShape =
            throw IllegalArgumentException("plugin inspection failed")
    }


    private class ProbeReader(
        name: String,
        compatibility: String
    ): ReaderCapability by TestServiceReaderCapability(), ReaderProbeCapability {
        override val identity = ReaderCapabilityIdentity("third.party.test", name, compatibility)
        override val readerCompatibility: String = compatibility

        override suspend fun probe(request: ReaderProbeRequest): ReaderProbeResult = ReaderProbeResult.NoMatch
    }


    private class AuthoringReader(
        name: String,
        compatibility: String
    ): ReaderCapability by TestServiceReaderCapability(), FormatAuthoringCapability {
        override val identity = ReaderCapabilityIdentity("third.party.test", name, compatibility)
        override val authoringIdentity: String = "shared-authoring-v1"

        override fun materialize(request: FormatMaterializationRequest): FormatMaterializationResult =
            error("Not used by duplicate-registration proof")
    }


    private class CloseFailureProvider(
        private val fingerprint: tech.kzen.auto.common.data.read.DataContentFingerprint
    ): DataContentProvider {
        var closeCount = 0
            private set

        override suspend fun describe(
            context: DataContext,
            ref: DataRef,
            control: ContentReadControl
        ): DataContentDescriptor = DataContentDescriptor(
            ref, setOf(ContentCapabilityIdentity.sequentialBytes))

        override suspend fun acquire(
            context: DataContext,
            ref: DataRef,
            control: ContentReadControl
        ): DataContentHandle = DataContentHandle(
            DataContentDescriptor(ref, setOf(ContentCapabilityIdentity.sequentialBytes)),
            fingerprint,
            object: SequentialByteContent {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1

                override fun close() {
                    closeCount++
                    error("byte close failed")
                }
            })
    }
}
