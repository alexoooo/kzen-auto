package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.detection.FormatHintMetadata
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.PlainTextReadConfig
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.read.text.PlainTextReader
import tech.kzen.auto.server.data.read.text.PlainTextReaderCapability
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest


@Reflect
class PlainTextFormat(
    override val title: String,
    override val extensions: List<String>,
    override val catalogVisible: Boolean,
    charset: String,
    bom: String,
    malformed: String,
    unmappable: String,
    private val contentCodings: List<String> = emptyList()
): ConfiguredRecordFormat {
    override val automaticDetectionCandidate: Boolean = false
    override val automaticTextFallback: Boolean = true
    override val authoringCapabilityIdentity: String
        get() = PlainTextReaderCapability.authoringIdentity
    override val hintMetadata: List<FormatHintMetadata> = listOf(
        FormatHintMetadata.semanticText(listOf("md"), listOf("text/markdown")),
        FormatHintMetadata.genericText(listOf("txt", "log"), listOf("text/plain")))
    private val baseConfig = PlainTextReadConfig(
        CharacterDecodingSpec(charset, bom, malformed, unmappable))

    init {
        PlainTextReaderCapability.validate(baseConfig)
    }

    override suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult {
        require(request.explicitEncoding?.lowercase() != "binary") {
            "Plain text requires a character encoding"
        }
        val config = request.explicitEncoding?.let { encoding ->
            baseConfig.copy(characters = baseConfig.characters.copy(charset = encoding))
        } ?: baseConfig
        val read = resolvedRead(request.ref, config)
        return FormatResolutionResult(read, FormatResolutionDetail(
            request.ref,
            null,
            title,
            selectionKind,
            FormatResolutionBasis.Override,
            request.explicitEncoding?.let { "$title was selected explicitly with $it" }
                ?: "$title was selected explicitly",
            resolvedEncoding = config.characters.charset))
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun resolvedRead(ref: DataRef): ResolvedReadSpec = resolvedRead(ref, baseConfig)

    private fun resolvedRead(ref: DataRef, config: PlainTextReadConfig): ResolvedReadSpec {
        val codings = if (contentCodings.isNotEmpty()) {
            contentCodings.map { ContentCodingSpec(it) }
        }
        else if (ref.id.lowercase().endsWith(".gz")) {
            listOf(ContentCodingSpec.gzip)
        }
        else {
            listOf(ContentCodingSpec.identity)
        }
        return ResolvedReadSpec(
            PlainTextReaderCapability.identity,
            codings,
            PlainTextReaderCapability.encode(config))
    }

    override fun declaredShape(): DataShape = DataShape(
        PlainTextReader.contract,
        tech.kzen.lib.common.exec.data.shape.ShapeProvenance.Declared,
        tech.kzen.lib.common.exec.data.shape.ShapeStability.Stable)

    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(baseConfig.asExecutionValue())
        sink.addInt(contentCodings.size)
        contentCodings.forEach(sink::addUtf8)
    }
}
