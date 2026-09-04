package tech.kzen.auto.server.data.read.text

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.format.FormatMaterializationRequest
import tech.kzen.auto.common.data.format.FormatMaterializationResult
import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.PlainTextReadConfig
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.api.data.ReaderCapability
import tech.kzen.auto.plugin.api.data.FormatAuthoringCapability
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.auto.server.data.content.character.CharacterDecoder
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import tech.kzen.auto.server.data.content.policy.ContentReadPolicy
import tech.kzen.auto.server.data.read.delimited.ReaderByteSequentialContent
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.platform.collect.toPersistentMap
import java.nio.charset.Charset
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds


object PlainTextReaderCapability: ReaderCapability, FormatAuthoringCapability {
    override val identity = ReaderCapabilityIdentity("tech.kzen.auto", "plain-text", "1")
    override val authoringIdentity = "tech.kzen.auto/plain-text-authoring-v1"

    override fun decode(config: ExecutionValue): ReaderConfig {
        val root = config as? MapExecutionValue
            ?: throw IllegalArgumentException("Plain-text reader config must be a map")
        val characters = root.values["characters"] as? MapExecutionValue
            ?: throw IllegalArgumentException("Plain-text reader characters must be a map")
        return PlainTextReadConfig(CharacterDecodingSpec(
            characters.text("charset"),
            characters.text("bom"),
            characters.text("malformed"),
            characters.text("unmappable")))
    }

    override fun validate(config: ReaderConfig) {
        require(config is PlainTextReadConfig) { "Plain-text reader config expected" }
        canonicalCharacters(config.characters)
    }

    override fun canonicalize(config: ReaderConfig): ReaderConfig {
        validate(config)
        return (config as PlainTextReadConfig).copy(characters = canonicalCharacters(config.characters))
    }

    override fun encode(config: ReaderConfig): ExecutionValue =
        (canonicalize(config) as PlainTextReadConfig).asExecutionValue()

    override fun requiredContent(config: ReaderConfig): ContentCapabilityIdentity {
        validate(config)
        return ContentCapabilityIdentity.sequentialBytes
    }

    override suspend fun open(request: ReaderOpenRequest): DataCursor = openCursor(request, Long.MAX_VALUE)

    override suspend fun inspect(request: ReaderInspectionRequest): DataShape =
        openCursor(request.open, request.maximumRecords).use { cursor ->
            var count = 0L
            while (count < request.maximumRecords && cursor.hasNext()) {
                cursor.next()
                count++
            }
            cursor.shape
        }

    override fun materialize(request: FormatMaterializationRequest): FormatMaterializationResult {
        require(request.overrides.isEmpty()) { "Plain-text format authoring does not accept quick overrides" }
        require(request.observedSchema == null) {
            "Plain-text columns are fixed by the reader and do not require a locked schema"
        }
        require(request.resolvedRead.reader == identity) {
            "Plain-text authoring requires the plain-text reader"
        }
        val canonical = canonicalize(decode(request.resolvedRead.config)) as PlainTextReadConfig
        val values = linkedMapOf(
            "is" to request.baseFormatReference,
            "catalogVisible" to "false",
            "charset" to canonical.characters.charset,
            "bom" to canonical.characters.bom,
            "malformed" to canonical.characters.malformed,
            "unmappable" to canonical.characters.unmappable)
        val entries: Map<AttributeSegment, AttributeNotation> = values.map { (key, value) ->
            AttributeSegment.ofKey(key) to ScalarAttributeNotation(value)
        }.toMap() + (AttributeSegment.ofKey("contentCodings") to ListAttributeNotation(
            request.resolvedRead.contentCodings.map { coding ->
                require(coding.config == MapExecutionValue(emptyMap())) {
                    "Plain-text formats cannot author content-coding config for '${coding.identity}'"
                }
                ScalarAttributeNotation(coding.identity)
            }.toPersistentList()))
        return FormatMaterializationResult(
            MapAttributeNotation(entries.toPersistentMap()),
            null,
            null,
            null,
            canonical.characters.charset)
    }

    private fun openCursor(request: ReaderOpenRequest, inspectionRecordLimit: Long): PlainTextDataCursor {
        val config = request.config as? PlainTextReadConfig
            ?: throw IllegalArgumentException("Plain-text reader config expected")
        val characters = CharacterDecoder.open(
            ReaderByteSequentialContent(request.bytes),
            config.characters,
            ContentReadControl(ContentReadPolicy(
                requireNotNull(request.policy.maximumExpandedBytes),
                requireNotNull(request.policy.timeoutMillis).milliseconds,
                inspectionRecordLimit)),
            request.sourceDisplay,
            request.part)
        val reader = PlainTextReader(characters, request.policy) {
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("Plain-text read interrupted")
            }
        }
        return PlainTextDataCursor(reader, DataShape(
            PlainTextReader.contract,
            ShapeProvenance.Declared,
            ShapeStability.Stable))
    }

    private fun canonicalCharacters(spec: CharacterDecodingSpec): CharacterDecodingSpec {
        require(!spec.charset.equals("auto", ignoreCase = true)) {
            "Configured readers require a resolved charset; 'auto' cannot be canonicalized before acquisition"
        }
        val charset = try {
            Charset.forName(spec.charset)
        }
        catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported charset '${spec.charset}'", failure)
        }
        require(!charset.name().equals("UTF-16", ignoreCase = true)) {
            "Configured readers require explicit UTF-16BE or UTF-16LE charset endianness"
        }
        val bom = spec.bom.lowercase()
        val malformed = spec.malformed.lowercase()
        val unmappable = spec.unmappable.lowercase()
        require(bom in setOf("detect", "permit", "require", "forbid"))
        require(malformed in setOf("report", "replace"))
        require(unmappable in setOf("report", "replace"))
        return spec.copy(charset = charset.name(), bom = bom, malformed = malformed, unmappable = unmappable)
    }

    private fun MapExecutionValue.text(name: String): String =
        (values[name] as? TextExecutionValue)?.value
            ?: throw IllegalArgumentException("Plain-text reader '$name' must be text")
}
