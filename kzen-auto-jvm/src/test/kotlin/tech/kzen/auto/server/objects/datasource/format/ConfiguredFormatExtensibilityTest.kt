package tech.kzen.auto.server.objects.datasource.format

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatMaterializationRequest
import tech.kzen.auto.common.data.format.FormatMaterializationResult
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.PlainTextReadConfig
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.api.data.FormatAuthoringCapability
import tech.kzen.auto.plugin.api.data.ReaderCapability
import tech.kzen.auto.plugin.api.data.ReaderProbeCapability
import tech.kzen.auto.plugin.api.data.ReaderProbeRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeResult
import tech.kzen.auto.plugin.api.data.ReaderProbeStrength
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.data.content.DirectDataContext
import tech.kzen.auto.server.data.content.FakeObjectStoreProvider
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.fakeFingerprint
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.auto.server.data.read.detection.AutomaticFormatResolver
import tech.kzen.auto.server.data.read.detection.AutomaticFormatResolutionObservation
import tech.kzen.auto.server.data.read.detection.AutomaticFormatResolverObserver
import tech.kzen.auto.server.data.read.detection.DetectionSampleAcquirer
import tech.kzen.auto.server.data.read.text.PlainTextReaderCapability
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.collect.toPersistentMap
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class ConfiguredFormatExtensibilityTest {
    @Test
    fun contributedProbeDetectsWithoutGenericBranchesAndNonauthoringActionsStayDisabled() = runBlocking {
        withContext { context ->
            val reference = "main/extensibility.yaml#main.detectable"
            val catalogEntry = catalogEntry(context, reference)

            assertFalse(catalogEntry.authoringAvailable)
            assertFalse(catalogEntry.columnLockingAvailable)
            assertEquals(null, catalogEntry.overrideEditorReference)
            assertTrue(catalogEntry.perFileOverrideAvailable)

            val source = DataSourceId("contributed-format")
            val fingerprint = fakeFingerprint("third-v1")
            val provider = FakeObjectStoreProvider(
                "THIRD:payload".encodeToByteArray(), fingerprint, expectedId = "sample.third")
            val observations = mutableListOf<AutomaticFormatResolutionObservation>()
            val resolver = AutomaticFormatResolver(
                context.configuredRecordFormatRegistry,
                context.readerCapabilityRegistry,
                DetectionSampleAcquirer(SequentialContentStack(DataContentProviderLookup(
                    LocalDataContentProvider(), mapOf(source to provider)))),
                observer = AutomaticFormatResolverObserver(observations::add))
            val result = resolver.resolve(FormatResolutionRequest(
                DirectDataContext,
                DataRef(source, "sample.third"),
                fingerprint,
                NormalizedFormatHints.of(filenameExtension = "third"),
                null))

            assertEquals(reference, result.detail.concreteFormatReference)
            assertEquals(TestContributedProbeReaderCapability.identity, result.resolvedRead.reader)
            assertEquals(1, TestContributedProbeReaderCapability.probeCount)
            assertEquals(1, observations.single().completeLogicalRecordsByCandidate[reference])
            assertEquals(provider.acquireCount, provider.closeCount)

            val failure = assertFailsWith<IllegalArgumentException> {
                context.configuredRecordFormatRegistry.materialize(
                    reference,
                    FormatMaterializationRequest(
                        reference,
                        result.resolvedRead,
                        null))
            }
            assertTrue(failure.message.orEmpty().contains("does not support quick correction"))
        }
    }


    @Test
    fun contributedProbeMatchesBinaryInputThatNoTextViewCanDecode() = runBlocking {
        // A feed decoder's sample is bytes, not text: detection must still run the extension's probe over the
        // bytes (no character view) and only report the text failure when nothing structured matched.
        withContext { context ->
            val reference = "main/extensibility.yaml#main.detectable"
            val source = DataSourceId("contributed-binary")
            val fingerprint = fakeFingerprint("third-binary-v1")
            val payload = byteArrayOf(0, 0xff.toByte(), 0x81.toByte()) + "THIRD:payload".encodeToByteArray()
            val provider = FakeObjectStoreProvider(payload, fingerprint, expectedId = "sample.third")
            val resolver = AutomaticFormatResolver(
                context.configuredRecordFormatRegistry,
                context.readerCapabilityRegistry,
                DetectionSampleAcquirer(SequentialContentStack(DataContentProviderLookup(
                    LocalDataContentProvider(), mapOf(source to provider)))))
            val result = resolver.resolve(FormatResolutionRequest(
                DirectDataContext,
                DataRef(source, "sample.third"),
                fingerprint,
                NormalizedFormatHints.of(filenameExtension = "third"),
                null))

            assertEquals(reference, result.detail.concreteFormatReference)
            assertEquals(TestContributedProbeReaderCapability.identity, result.resolvedRead.reader)
            assertEquals(null, result.detail.resolvedEncoding)

            // Binary that no format claims still fails as not-text, in the decoder's own words.
            val unclaimed = FakeObjectStoreProvider(
                byteArrayOf(0, 0xff.toByte(), 0x81.toByte(), 0x00), fingerprint, expectedId = "sample.bin")
            val unclaimedResolver = AutomaticFormatResolver(
                context.configuredRecordFormatRegistry,
                context.readerCapabilityRegistry,
                DetectionSampleAcquirer(SequentialContentStack(DataContentProviderLookup(
                    LocalDataContentProvider(), mapOf(source to unclaimed)))))
            val failure = assertFailsWith<tech.kzen.auto.server.data.read.detection.FormatDetectionException> {
                unclaimedResolver.resolve(FormatResolutionRequest(
                    DirectDataContext, DataRef(source, "sample.bin"), fingerprint,
                    NormalizedFormatHints.of(filenameExtension = null), null))
            }
            assertTrue(failure.message.orEmpty().contains("not valid UTF-8"), failure.message)
        }
    }


    @Test
    fun contributedSignatureOutranksTheBuiltInDelimitedGuess() = runBlocking {
        // Regular comma-separated text is a structural match for the built-in delimited reader; a reader that
        // recognizes the content's own signature must win rather than tie into the text fallback.
        withContext { context ->
            val source = DataSourceId("contributed-signature")
            val fingerprint = fakeFingerprint("third-signature-v1")
            val provider = FakeObjectStoreProvider(
                "THIRD:,marker\n1,2\n3,4\n".encodeToByteArray(), fingerprint, expectedId = "table.txt")
            val resolver = AutomaticFormatResolver(
                context.configuredRecordFormatRegistry,
                context.readerCapabilityRegistry,
                DetectionSampleAcquirer(SequentialContentStack(DataContentProviderLookup(
                    LocalDataContentProvider(), mapOf(source to provider)))))
            val result = resolver.resolve(FormatResolutionRequest(
                DirectDataContext,
                DataRef(source, "table.txt"),
                fingerprint,
                NormalizedFormatHints.of(filenameExtension = "txt"),
                null))

            assertEquals("main/extensibility.yaml#main.detectable", result.detail.concreteFormatReference)
            assertEquals(TestContributedProbeReaderCapability.identity, result.resolvedRead.reader)
            assertEquals("UTF-8", result.detail.resolvedEncoding)
        }
    }


    @Test
    fun contributedAuthoringCapabilityPublishesItsEditorThroughTheGenericCatalog() = runBlocking {
        withContext { context ->
            val reference = "main/extensibility.yaml#main.editor"
            val entry = catalogEntry(context, reference)

            assertEquals(TestContributedEditorReaderCapability.authoringIdentity,
                entry.authoringCapabilityIdentity)
            assertEquals("third-party/editor.yaml#CustomFormatEditor", entry.overrideEditorReference)
            assertTrue(entry.authoringAvailable)
            assertFalse(entry.columnLockingAvailable)
            assertTrue(entry.perFileOverrideAvailable)

            val resolved = context.configuredRecordFormatRegistry.resolve(
                reference,
                FormatResolutionRequest(
                    DirectDataContext,
                    DataRef(null, "opaque-object-without-a-suffix"),
                    null,
                    NormalizedFormatHints.empty,
                    null))
            val authoritative = resolved.resolvedRead.copy(
                contentCodings = listOf(ContentCodingSpec("third-party/envelope-v2")))
            context.configuredRecordFormatRegistry.materialize(
                reference,
                FormatMaterializationRequest(reference, authoritative, null))

            assertEquals(authoritative, TestContributedEditorReaderCapability.lastMaterializedRead)
        }
    }


    private suspend fun withContext(block: suspend (KzenAutoContext) -> Unit) {
        val root = Files.createTempDirectory("configured-format-extensibility")
        val notation = root.resolve("src/main/resources/notation/main")
        Files.createDirectories(notation)
        Files.writeString(notation.resolve("extensibility.yaml"), """
            TestContributedFormat:
              abstract: true
              is: auto-jvm/datasource/configured-delimited-format.yaml#ConfiguredRecordFormat
              class: tech.kzen.auto.server.objects.datasource.format.TestContributedFormat
              title: "Contributed format"
              extensions: []
              catalogVisible: true
              readerMode: probe
              editorReference: ""
              meta:
                readerMode: String
                editorReference: String

            main:
              is: Job

            main.detectable:
              is: TestContributedFormat
              title: "Third-party detectable"
              extensions:
                - third
              catalogVisible: true
              readerMode: probe
              editorReference: ""

            main.editor:
              is: TestContributedFormat
              title: "Third-party editor"
              extensions:
                - third-edit
              catalogVisible: true
              readerMode: editor
              editorReference: third-party/editor.yaml#CustomFormatEditor
        """.trimIndent())

        val context = KzenAutoContext.create(KzenAutoConfig(
            jsModuleName = "kzen-auto-js",
            moduleRoot = root))
        try {
            TestContributedProbeReaderCapability.probeCount = 0
            TestContributedEditorReaderCapability.lastMaterializedRead = null
            block(context)
        }
        finally {
            context.close()
            WorkUtils.recursivelyDeleteDir(root)
        }
    }


    private suspend fun catalogEntry(
        context: KzenAutoContext,
        reference: String
    ): tech.kzen.auto.common.data.format.ConfiguredFormatDetail {
        val entries = context.configuredRecordFormatRegistry.catalog().formats
        return entries.singleOrNull { it.reference == reference } ?: error(
            "Missing $reference from ${entries.map { it.reference }}; direct preflight: " +
                runCatching { context.configuredRecordFormatRegistry.preflight(reference) }
                    .exceptionOrNull()?.message)
    }
}


@Reflect
class TestContributedFormat(
    override val title: String,
    override val extensions: List<String>,
    override val catalogVisible: Boolean,
    private val readerMode: String,
    private val editorReference: String
): ConfiguredRecordFormat {
    private val capability: ReaderCapability = when (readerMode) {
        "probe" -> TestContributedProbeReaderCapability()
        "editor" -> TestContributedEditorReaderCapability()
        else -> error("Unknown contributed reader mode: $readerMode")
    }

    override val automaticDetectionCandidate: Boolean
        get() = readerMode == "probe"
    override val automaticDetectionTemplate: Boolean
        get() = readerMode == "probe"
    override val authoringCapabilityIdentity: String?
        get() = if (readerMode == "editor") TestContributedEditorReaderCapability.authoringIdentity else null
    override val overrideEditorReference: String?
        get() = editorReference.takeIf(String::isNotEmpty)


    @Suppress("OVERRIDE_DEPRECATION")
    override fun resolvedRead(ref: DataRef): ResolvedReadSpec = ResolvedReadSpec(
        capability.identity,
        listOf(ContentCodingSpec.identity),
        capability.encode(PlainTextReadConfig(CharacterDecodingSpec(
            "UTF-8", "permit", "report", "report"))))


    override fun declaredShape(): DataShape? = null


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(title)
        sink.addUtf8(readerMode)
        sink.addUtf8(editorReference)
    }
}


class TestContributedProbeReaderCapability:
    ReaderCapability by PlainTextReaderCapability,
    ReaderProbeCapability {
    override val identity: ReaderCapabilityIdentity
        get() = Companion.identity
    override val readerCompatibility: String = identity.compatibility


    override suspend fun probe(request: ReaderProbeRequest): ReaderProbeResult {
        probeCount += 1
        // Text when the host could decode one, else the raw bytes — the sentinel may sit behind binary framing.
        val text = request.characterViews.singleOrNull()?.text
            ?: request.sample.toByteArray().toString(Charsets.ISO_8859_1)
        return if (text.contains("THIRD:")) {
            request.observer.completeLogicalRecordsConsidered(1)
            ReaderProbeResult.Matched(
                ReaderProbeStrength.ContentSignature,
                request.candidateConfig,
                "Third-party sentinel matched")
        }
        else ReaderProbeResult.NoMatch
    }


    companion object {
        val identity = ReaderCapabilityIdentity("third.party.test", "probe-reader", "third-probe-v1")
        var probeCount: Int = 0
    }
}


class TestContributedEditorReaderCapability:
    ReaderCapability by PlainTextReaderCapability,
    FormatAuthoringCapability {
    override val identity: ReaderCapabilityIdentity
        get() = Companion.identity
    override val authoringIdentity: String = Companion.authoringIdentity


    override fun materialize(request: FormatMaterializationRequest): FormatMaterializationResult =
        FormatMaterializationResult(
            MapAttributeNotation(mapOf(
                AttributeSegment.ofKey("is") to ScalarAttributeNotation(request.baseFormatReference),
                AttributeSegment.ofKey("catalogVisible") to ScalarAttributeNotation("false")
            ).toPersistentMap()),
            null,
            null,
            null,
            null).also {
            lastMaterializedRead = request.resolvedRead
        }


    companion object {
        val identity = ReaderCapabilityIdentity("third.party.test", "editor-reader", "1")
        const val authoringIdentity = "third.party.test/editor-authoring-v1"
        var lastMaterializedRead: ResolvedReadSpec? = null
    }
}
