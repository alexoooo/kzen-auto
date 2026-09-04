package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.DelimitedDialectSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.FieldDecodeOverride
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.common.data.read.TypedDecodePolicy
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.AuthoredRecordSchemaNotation
import tech.kzen.auto.plugin.api.data.ReaderCapability
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeCapability
import tech.kzen.auto.plugin.api.data.ReaderProbeRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeResult
import tech.kzen.auto.plugin.api.data.FormatAuthoringCapability
import tech.kzen.auto.common.data.format.DelimitedFormatOverrideConventions
import tech.kzen.auto.common.data.format.FormatMaterializationRequest
import tech.kzen.auto.common.data.format.FormatMaterializationResult
import tech.kzen.auto.common.data.format.FormatOverrideEditorMetadata
import tech.kzen.auto.server.data.content.character.CharacterDecoder
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import tech.kzen.auto.server.data.content.policy.ContentReadPolicy
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.shape.SampleCoverage
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


object ConfiguredDelimitedReaderCapability:
    ReaderCapability,
    ReaderProbeCapability,
    FormatAuthoringCapability
{
    override val identity = ReaderCapabilityIdentity(
        "tech.kzen.auto", "configured-delimited", "1")
    override val readerCompatibility: String = identity.compatibility
    override val authoringIdentity = "tech.kzen.auto/configured-delimited-authoring-v1"
    override val supportsColumnLocking: Boolean = true

    override fun decode(config: ExecutionValue): ReaderConfig {
        val root = config.map("configured-delimited")
        val framing = root.mapAt("framing")
        val dialect = root.mapAt("dialect")
        val header = root.mapAt("header")
        val characters = root.mapAt("characters")
        val typed = root.mapAt("typedDecode")
        val schema = when (val encoded = root.valueAt("schema")) {
            NullExecutionValue -> null
            else -> DataContract.ofExecutionValue(encoded)
        }
        val overrides = typed.listAt("fieldOverrides").values.map { encoded ->
            val override = encoded.map("field override")
            FieldDecodeOverride(
                override.listAt("path").values.map { it.text("field path") },
                override.nullableTextAt("nullToken"))
        }
        return DelimitedReadConfig(
            RecordFramingSpec(framing.textAt("separator")),
            DelimitedDialectSpec(
                dialect.textAt("delimiter"),
                dialect.nullableTextAt("quote"),
                dialect.textAt("escape"),
                dialect.textAt("emptyField"),
                dialect.textAt("trimming")),
            HeaderReadSpec(header.textAt("policy"), header.textAt("mapping")),
            CharacterDecodingSpec(
                characters.textAt("charset"),
                characters.textAt("bom"),
                characters.textAt("malformed"),
                characters.textAt("unmappable")),
            schema,
            TypedDecodePolicy(
                typed.nullableTextAt("nullToken"),
                typed.textAt("malformedValue"),
                overrides),
            root.optionalNonNegativeIntAt("skipLeadingLines") ?: 0,
            root.optionalNullableTextAt("commentPrefix"))
    }

    override fun validate(config: ReaderConfig) {
        require(config is DelimitedReadConfig) { "Delimited reader config expected" }
        require(config.dialect.delimiter.length == 1) { "Delimiter must contain exactly one character" }
        val quote = config.dialect.quote
        require(quote == null || quote.length == 1) {
            "Quote must contain exactly one character when enabled"
        }
        require(quote == null || config.dialect.delimiter != quote) {
            "Delimiter and quote must be different characters"
        }
        require(config.framing.separator in setOf("lf", "crlf"))
        require(config.dialect.escape in setOf("double-quote", "none"))
        require(config.dialect.emptyField == "empty")
        require(config.dialect.trimming in setOf("unquoted", "none"))
        require(config.header.policy in setOf("present", "absent", "infer-labels"))
        require(config.header.mapping == "exact-name")
        require(config.typedDecode.malformedValue == "fail-part")
        require(config.skipLeadingLines >= 0) { "Leading-line skip must not be negative" }
        val commentPrefix = config.commentPrefix
        require(commentPrefix == null || commentPrefix.isNotEmpty()) {
            "Comment prefix must not be empty when configured"
        }
        require(commentPrefix?.none { it == '\r' || it == '\n' } != false) {
            "Comment prefix must not contain a record separator"
        }
        val characters = canonicalCharacters(config.characters)
        require(characters.bom in setOf("detect", "permit", "require", "forbid")) {
            "Unsupported BOM policy '${config.characters.bom}'"
        }
        require(characters.malformed in setOf("report", "replace")) {
            "Unsupported malformed-input policy '${config.characters.malformed}'"
        }
        require(characters.unmappable in setOf("report", "replace")) {
            "Unsupported unmappable-character policy '${config.characters.unmappable}'"
        }
        require(config.header.policy != "absent" || config.schema != null) {
            "Header-absent input requires a declared schema"
        }
        require(config.header.policy != "infer-labels" || config.schema == null) {
            "Infer-labels is only valid without a declared schema"
        }
        val schema = config.schema
        if (schema == null) {
            require(config.typedDecode.fieldOverrides.isEmpty()) {
                "Typed field overrides require a declared schema"
            }
        }
        else {
            validateDelimitedTypedConfig(
                schema,
                config.typedDecode,
                DelimitedReadContext("reader-configuration"))
        }
    }

    override fun canonicalize(config: ReaderConfig): ReaderConfig {
        validate(config)
        config as DelimitedReadConfig
        return config.copy(characters = canonicalCharacters(config.characters))
    }

    override fun encode(config: ReaderConfig): ExecutionValue {
        return (canonicalize(config) as DelimitedReadConfig).asExecutionValue()
    }

    override fun requiredContent(config: ReaderConfig): ContentCapabilityIdentity {
        validate(config)
        return ContentCapabilityIdentity.sequentialBytes
    }


    override suspend fun open(request: ReaderOpenRequest): DataCursor =
        openCursor(request, Long.MAX_VALUE)


    override suspend fun inspect(request: ReaderInspectionRequest): DataShape =
        openCursor(request.open, request.maximumRecords).use { cursor ->
            var inspected = 0L
            var complete = false
            val limit = request.maximumRecords
            while (inspected < limit) {
                if (!cursor.hasNext()) {
                    complete = true
                    break
                }
                cursor.next()
                inspected++
            }
            inspectionShape(cursor, inspected, complete)
        }


    override suspend fun probe(request: ReaderProbeRequest): ReaderProbeResult =
        DelimitedProbe.probe(request)


    override fun materialize(request: FormatMaterializationRequest): FormatMaterializationResult {
        val unsupported = request.overrides.keys - DelimitedFormatOverrideConventions.supported
        require(unsupported.isEmpty()) { "Unsupported delimited overrides: ${unsupported.sorted().joinToString()}" }
        require(request.resolvedRead.reader == identity) {
            "Delimited authoring requires the configured-delimited reader"
        }
        val decoded = decode(request.resolvedRead.config) as DelimitedReadConfig
        require(decoded.schema == null || request.observedSchema == null) {
            "An observed schema cannot replace a configured schema during quick correction"
        }
        val delimiter = requiredOverrideOrDefault(
            request.overrides, DelimitedFormatOverrideConventions.delimiter, decoded.dialect.delimiter)
        val header = requiredOverrideOrDefault(
            request.overrides, DelimitedFormatOverrideConventions.header, decoded.header.policy)
        val charset = requiredOverrideOrDefault(
            request.overrides, DelimitedFormatOverrideConventions.encoding, decoded.characters.charset)
        val skipLeadingLines = if (DelimitedFormatOverrideConventions.skipLeadingLines in request.overrides) {
            val raw = requireNotNull(request.overrides[DelimitedFormatOverrideConventions.skipLeadingLines]) {
                "'${DelimitedFormatOverrideConventions.skipLeadingLines}' override must not be null"
            }
            raw.toIntOrNull() ?: throw IllegalArgumentException(
                "'${DelimitedFormatOverrideConventions.skipLeadingLines}' override must be an integer")
        }
        else {
            decoded.skipLeadingLines
        }
        val commentPrefix = if (DelimitedFormatOverrideConventions.commentPrefix in request.overrides) {
            request.overrides[DelimitedFormatOverrideConventions.commentPrefix]
        }
        else {
            decoded.commentPrefix
        }
        val observedSchema = request.observedSchema
        val materialized = decoded.copy(
            dialect = decoded.dialect.copy(delimiter = delimiter),
            header = decoded.header.copy(policy = if (observedSchema != null && header == "infer-labels") {
                "absent"
            }
            else {
                header
            }),
            characters = decoded.characters.copy(charset = charset),
            schema = observedSchema ?: decoded.schema,
            skipLeadingLines = skipLeadingLines,
            commentPrefix = commentPrefix)
        val canonical = canonicalize(materialized) as DelimitedReadConfig
        val values = linkedMapOf(
            "is" to request.baseFormatReference,
            "catalogVisible" to "false",
            "delimiter" to canonical.dialect.delimiter,
            "quote" to canonical.dialect.quote.orEmpty(),
            "escape" to canonical.dialect.escape,
            "recordSeparator" to canonical.framing.separator,
            "trimming" to canonical.dialect.trimming,
            "header" to canonical.header.policy,
            "charset" to canonical.characters.charset,
            "bom" to canonical.characters.bom,
            "malformed" to canonical.characters.malformed,
            "unmappable" to canonical.characters.unmappable,
            "nullToken" to canonical.typedDecode.nullToken.orEmpty(),
            "skipLeadingLines" to canonical.skipLeadingLines.toString(),
            "commentPrefix" to canonical.commentPrefix.orEmpty())
        val entries: Map<AttributeSegment, AttributeNotation> = values.map { (key, value) ->
            AttributeSegment.ofKey(key) to ScalarAttributeNotation(value)
        }.toMap() + (AttributeSegment.ofKey("contentCodings") to ListAttributeNotation(
            request.resolvedRead.contentCodings.map { coding ->
                require(coding.config == MapExecutionValue(emptyMap())) {
                    "Configured delimited formats cannot author content-coding config for '${coding.identity}'"
                }
                ScalarAttributeNotation(coding.identity)
            }.toPersistentList()))
        val body = MapAttributeNotation(entries.toPersistentMap())
        val schemaBody = observedSchema?.let { contract ->
            AuthoredRecordSchemaNotation.body(contract)
                ?: throw IllegalArgumentException(
                    "Observed columns cannot be represented as an authored record schema")
        }
        return FormatMaterializationResult(
            body,
            schemaBody,
            schemaBody?.let { "schema" },
            FormatOverrideEditorMetadata(overrideEditorReference, "Delimited options"),
            canonical.characters.charset)
    }


    private fun requiredOverrideOrDefault(
        overrides: Map<String, String?>,
        key: String,
        default: String
    ): String {
        if (key !in overrides) return default
        return requireNotNull(overrides[key]) { "'$key' override must not be null" }
    }


    private fun openCursor(
        request: ReaderOpenRequest,
        inspectionRecordLimit: Long
    ): ConfiguredDelimitedDataCursor {
        val delimited = request.config as? DelimitedReadConfig
            ?: throw IllegalArgumentException("Delimited reader config expected")
        val characters = CharacterDecoder.open(
            ReaderByteSequentialContent(request.bytes),
            delimited.characters,
            ContentReadControl(ContentReadPolicy(
                requireNotNull(request.policy.maximumExpandedBytes),
                requireNotNull(request.policy.timeoutMillis).milliseconds,
                inspectionRecordLimit)),
            request.sourceDisplay,
            request.part)
        val reader = ConfiguredDelimitedReader.open(
            characters,
            delimited,
            request.policy,
            DelimitedReadContext(request.sourceDisplay, part = request.part)) {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("Delimited read interrupted")
                }
            }
        val declared = delimited.schema != null
        val emptyObserved = !declared && reader.observedDuringOpen == 0L
        val shape = DataShape(
            reader.contract,
            if (declared) ShapeProvenance.Declared else ShapeProvenance.Inferred,
            if (declared || emptyObserved) {
                ShapeStability.Stable
            }
            else {
                ShapeStability.Provisional(SampleCoverage(reader.observedDuringOpen))
            })
        return ConfiguredDelimitedDataCursor(
            reader,
            shape,
            { request.bytes.expandedBytesRead })
    }


    private fun inspectionShape(
        cursor: ConfiguredDelimitedDataCursor,
        observedRecords: Long,
        complete: Boolean
    ): DataShape {
        val shape = cursor.shape
        if (shape.provenance == ShapeProvenance.Declared) return shape
        val stability = if (observedRecords == 0L) {
            check(complete) { "A positive inspection record limit cannot produce empty partial coverage" }
            ShapeStability.Stable
        }
        else {
            ShapeStability.Provisional(SampleCoverage(
                observedRecords,
                cursor.expandedBytesRead?.takeIf { it > 0 },
                complete))
        }
        return DataShape(shape.itemType, shape.provenance, stability, shape.diagnostics)
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
        return spec.copy(
            charset = charset.name(),
            bom = spec.bom.lowercase(),
            malformed = spec.malformed.lowercase(),
            unmappable = spec.unmappable.lowercase())
    }


    const val overrideEditorReference =
        "auto-js/datasource/delimited-format-override-editor.yaml#DelimitedFormatOverrideEditor"
}


private fun ExecutionValue.map(label: String): MapExecutionValue =
    this as? MapExecutionValue ?: throw IllegalArgumentException("$label must be a map")

private fun ExecutionValue.text(label: String): String =
    (this as? TextExecutionValue)?.value
        ?: throw IllegalArgumentException("$label must be text")

private fun MapExecutionValue.valueAt(name: String): ExecutionValue = values[name]
    ?: throw IllegalArgumentException("'$name' missing")

private fun MapExecutionValue.mapAt(name: String): MapExecutionValue = valueAt(name).map(name)

private fun MapExecutionValue.listAt(name: String): ListExecutionValue =
    valueAt(name) as? ListExecutionValue
        ?: throw IllegalArgumentException("'$name' must be a list")

private fun MapExecutionValue.textAt(name: String): String = valueAt(name).text(name)

private fun MapExecutionValue.nullableTextAt(name: String): String? = when (val value = valueAt(name)) {
    NullExecutionValue -> null
    else -> value.text(name)
}

private fun MapExecutionValue.optionalNonNegativeIntAt(name: String): Int? = when (val value = values[name]) {
    null -> null
    is LongExecutionValue -> value.value.also {
        require(it in 0..Int.MAX_VALUE.toLong()) { "'$name' is outside the supported range" }
    }.toInt()
    else -> throw IllegalArgumentException("'$name' must be an integer")
}

private fun MapExecutionValue.optionalNullableTextAt(name: String): String? = when (val value = values[name]) {
    null, NullExecutionValue -> null
    else -> value.text(name)
}
