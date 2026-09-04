package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.DelimitedDialectSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.read.TypedDecodePolicy
import tech.kzen.auto.common.data.schema.RecordSchema
import tech.kzen.auto.common.data.schema.declaredShape
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest


@Reflect
class ConfiguredDelimitedFormat(
    override val title: String,
    override val extensions: List<String>,
    override val catalogVisible: Boolean,
    delimiter: String,
    quote: String,
    escape: String,
    recordSeparator: String,
    trimming: String,
    header: String,
    charset: String,
    bom: String,
    malformed: String,
    unmappable: String,
    nullToken: String,
    private val schema: RecordSchema?,
    skipLeadingLines: Int = 0,
    commentPrefix: String = "",
    override val compatibleStructuredFamilies: List<String> = extensions,
    override val automaticDetectionTemplate: Boolean = false,
    private val contentCodings: List<String> = emptyList()
): ConfiguredRecordFormat {
    override val authoringCapabilityIdentity: String
        get() = ConfiguredDelimitedReaderCapability.authoringIdentity

    override val overrideEditorReference: String
        get() = ConfiguredDelimitedReaderCapability.overrideEditorReference

    override val columnsLocked: Boolean
        get() = schema != null

    private val baseConfig = DelimitedReadConfig(
        RecordFramingSpec(recordSeparator),
        DelimitedDialectSpec(
            delimiter,
            quote.takeIf { it.isNotEmpty() },
            escape,
            "empty",
            trimming),
        HeaderReadSpec(header, "exact-name"),
        CharacterDecodingSpec(charset, bom, malformed, unmappable),
        schema?.contract(),
        TypedDecodePolicy(
            nullToken.takeIf { it.isNotEmpty() },
            "fail-part",
            emptyList()),
        skipLeadingLines,
        commentPrefix.takeIf(String::isNotEmpty))

    init {
        ConfiguredDelimitedReaderCapability.validate(baseConfig)
        require(extensions.none(String::isBlank)) { "Format extensions must not be blank" }
    }


    override suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult {
        val explicitEncoding = request.explicitEncoding
        require(explicitEncoding?.lowercase() != "binary") {
            "Delimited data requires a character encoding"
        }
        val config = if (explicitEncoding == null) {
            baseConfig
        }
        else {
            baseConfig.copy(characters = baseConfig.characters.copy(charset = explicitEncoding))
        }
        ConfiguredDelimitedReaderCapability.validate(config)
        return FormatResolutionResult(
            resolvedRead(request.ref, config),
            FormatResolutionDetail(
                request.ref,
                null,
                title,
                selectionKind,
                FormatResolutionBasis.Override,
                if (explicitEncoding == null) {
                    "$title was selected explicitly"
                }
                else {
                    "$title was selected explicitly with $explicitEncoding"
                },
                resolvedEncoding = config.characters.charset,
                columnsLocked = columnsLocked))
    }


    @Suppress("OVERRIDE_DEPRECATION")
    override fun resolvedRead(ref: DataRef): ResolvedReadSpec = resolvedRead(ref, baseConfig)


    private fun resolvedRead(ref: DataRef, config: DelimitedReadConfig): ResolvedReadSpec {
        val encoded = ConfiguredDelimitedReaderCapability.encode(config)
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
            ConfiguredDelimitedReaderCapability.identity,
            codings,
            encoded)
    }


    override fun declaredShape(): tech.kzen.auto.common.data.schema.DataShape? = schema?.declaredShape()


    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(baseConfig.asExecutionValue())
        sink.addInt(contentCodings.size)
        contentCodings.forEach(sink::addUtf8)
    }
}
