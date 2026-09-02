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
import tech.kzen.auto.plugin.api.data.ReaderCapability
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.auto.server.data.content.character.CharacterDecoder
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import tech.kzen.auto.server.data.content.policy.ContentReadPolicy
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.shape.SampleCoverage
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import java.nio.charset.Charset
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds


object ConfiguredDelimitedReaderCapability: ReaderCapability {
    override val identity = ReaderCapabilityIdentity(
        "tech.kzen.auto", "configured-delimited", "1")

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
                overrides))
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
