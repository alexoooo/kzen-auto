package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
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
    private val schema: RecordSchema?
): ConfiguredRecordFormat {
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
            emptyList()))

    init {
        ConfiguredDelimitedReaderCapability.validate(baseConfig)
        require(extensions.none(String::isBlank)) { "Format extensions must not be blank" }
    }


    override fun resolvedRead(ref: DataRef): ResolvedReadSpec {
        val encoded = ConfiguredDelimitedReaderCapability.encode(baseConfig)
        val coding = if (ref.id.lowercase().endsWith(".gz")) {
            ContentCodingSpec.gzip
        }
        else {
            ContentCodingSpec.identity
        }
        return ResolvedReadSpec(
            ConfiguredDelimitedReaderCapability.identity,
            listOf(coding),
            encoded)
    }


    override fun declaredShape(): tech.kzen.auto.common.data.schema.DataShape? = schema?.declaredShape()


    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(baseConfig.asExecutionValue())
    }
}
