package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.FieldDecodeOverride
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueAccess
import java.math.BigDecimal
import java.math.BigInteger


/** Strict configured delimited parser over provider-neutral sequential characters. */
class ConfiguredDelimitedReader private constructor(
    private val input: SequentialCharacterContent,
    private val parser: Parser,
    private val context: DelimitedReadContext,
    private val physicalByOutput: IntArray,
    val contract: DataContract,
    val observedDuringOpen: Long,
    private val typed: TypedDecoder,
    private var pending: RawRecord?
): AutoCloseable {
    val expandedBytesRead: Long? get() = input.expandedBytesRead

    companion object {
        fun open(
            input: SequentialCharacterContent,
            config: DelimitedReadConfig,
            policy: ReadOperationalPolicy,
            context: DelimitedReadContext,
            checkpoint: () -> Unit = {}
        ): ConfiguredDelimitedReader {
            try {
                val parser = Parser(input, SyntaxSpec.of(config), Limits.of(policy), context, checkpoint)
                val declared = config.schema
                val headerPolicy = config.header.policy
                val first = when (headerPolicy) {
                    "present", "infer-labels" -> parser.readRecord()
                    "absent" -> null
                    else -> invalid(context, "Unsupported header policy '$headerPolicy'")
                }

                val plan = when (headerPolicy) {
                    "present" -> headerPlan(first, declared, config.header.mapping, context)
                    "absent" -> declaredPlan(declared ?: invalid(
                        context, "Header-absent input requires a declared schema"), context)
                    else -> inferredPlan(first, declared, context)
                }
                val typed = TypedDecoder(plan.contract, config.typedDecode, context)
                return ConfiguredDelimitedReader(
                    input, parser, context, plan.mapping, plan.contract, if (first == null) 0 else 1, typed,
                    if (headerPolicy == "infer-labels") first else null)
            }
            catch (failure: Throwable) {
                try {
                    input.close()
                }
                catch (closeFailure: Throwable) {
                    failure.addSuppressed(closeFailure)
                }
                throw failure
            }
        }

        private fun headerPlan(
            header: RawRecord?,
            declared: DataContract?,
            mappingPolicy: String,
            context: DelimitedReadContext
        ): ProjectionPlan {
            if (mappingPolicy != "exact-name") {
                invalid(context, "Unsupported header mapping '$mappingPolicy'")
            }
            if (header == null) {
                return declared?.let { declaredPlan(it) }
                    ?: ProjectionPlan(observedTextContract(emptyList()), IntArray(0))
            }
            val labels = header.fields.map { it.text }
            val duplicates = labels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
            if (duplicates.isNotEmpty()) {
                headerFailure(context, "Duplicate labels: ${duplicates.joinToString()}")
            }
            if (declared == null) {
                return ProjectionPlan(observedTextContract(labels), labels.indices.toList().toIntArray())
            }
            val declaredFields = recordFields(declared, context)
            val declaredNames = declaredFields.map { it.id.name }
            val repeatedDeclared = declaredNames.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (repeatedDeclared.isNotEmpty()) {
                invalid(context, "Delimited schemas require unique field names: ${repeatedDeclared.sorted()}")
            }
            val missing = (declaredNames.toSet() - labels.toSet()).sorted()
            val extra = (labels.toSet() - declaredNames.toSet()).sorted()
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                headerFailure(context, "Missing labels: ${missing.joinToString()}; extra labels: ${extra.joinToString()}")
            }
            return ProjectionPlan(declared, declaredNames.map(labels::indexOf).toIntArray())
        }

        private fun inferredPlan(
            first: RawRecord?,
            declared: DataContract?,
            context: DelimitedReadContext
        ): ProjectionPlan {
            if (declared != null) {
                invalid(context, "Infer-labels is only valid without a declared schema")
            }
            val width = first?.fields?.size ?: 0
            val labels = (0 until width).map { "c$it" }
            return ProjectionPlan(observedTextContract(labels), labels.indices.toList().toIntArray())
        }

        private fun declaredPlan(contract: DataContract, context: DelimitedReadContext? = null): ProjectionPlan {
            val fields = recordFields(contract, context ?: DelimitedReadContext("configuration"))
            return ProjectionPlan(contract, fields.indices.toList().toIntArray())
        }

        private fun observedTextContract(labels: List<String>): DataContract = DataContract(DataType.Record(
            labels.map { DataField(FieldId(it, 0), DataType.Scalar(ScalarKind.Text)) }))

        private fun recordFields(contract: DataContract, context: DelimitedReadContext): List<DataField> =
            (contract.structural as? DataType.Record)?.fields
                ?: invalid(context, "Delimited reader requires a record contract")

        private fun headerFailure(context: DelimitedReadContext, detail: String): Nothing =
            throw DelimitedReadException(DelimitedReadException.header, context, 1, detail = detail)

        private fun invalid(context: DelimitedReadContext, detail: String): Nothing =
            throw DelimitedReadException(DelimitedReadException.configuration, context, 0, detail = detail)
    }

    private var logicalRecordIndex = 0L

    fun read(): DelimitedRecord? {
        val raw = pending?.also { pending = null } ?: parser.readRecord() ?: return null
        logicalRecordIndex++
        if (raw.fields.size != physicalByOutput.size) {
            throw DelimitedReadException(
                DelimitedReadException.width, context, logicalRecordIndex,
                detail = "Expected ${physicalByOutput.size} fields but found ${raw.fields.size}")
        }
        val decoded = physicalByOutput.mapIndexed { outputIndex, physicalIndex ->
            typed.decode(outputIndex, raw.fields[physicalIndex], logicalRecordIndex)
        }
        val backing = FlatFileRecord.of(decoded.map { it.text })
        backing.attachHeader(FlatRecordHeader(contract))
        val access = NullAwareFlatRecordAccess(backing, decoded.map { it.isNull }.toBooleanArray())
        return DelimitedRecord(backing, access, DataValue(access, DataNode(0)))
    }

    override fun close() = input.close()

    private data class ProjectionPlan(val contract: DataContract, val mapping: IntArray)
}


private class NullAwareFlatRecordAccess(
    private val backing: FlatFileRecord,
    private val nulls: BooleanArray
): ValueAccess by backing {
    override fun state(node: DataNode): DataState {
        if (node.token == 0L) return DataState.Present
        val index = node.token.toInt() - 1
        if (index !in nulls.indices) return backing.state(node)
        return if (nulls[index]) DataState.Null else DataState.Present
    }

    override fun scalar(node: DataNode): ScalarExecutionValue = present(node) { backing.scalar(node) }
    override fun readBoolean(node: DataNode): Boolean = present(node) { backing.readBoolean(node) }
    override fun readLong(node: DataNode): Long = present(node) { backing.readLong(node) }
    override fun readDouble(node: DataNode): Double = present(node) { backing.readDouble(node) }
    override fun readText(node: DataNode): String = present(node) { backing.readText(node) }
    override fun readBinary(node: DataNode): ByteArray = present(node) { backing.readBinary(node) }
    override fun activeVariant(node: DataNode): VariantId = present(node) { backing.activeVariant(node) }
    override fun selected(node: DataNode): DataNode = present(node) { backing.selected(node) }

    private fun <T> present(node: DataNode, action: () -> T): T {
        if (state(node) == DataState.Null) {
            throw DataAccessException(DataProblem(DataProblem.invalidState, "Cannot read a null flat field"))
        }
        return action()
    }
}


private data class RawField(val text: String, val span: LongRange)
private data class RawRecord(val fields: List<RawField>)


private data class SyntaxSpec(
    val delimiter: Char,
    val quote: Char?,
    val doubledQuote: Boolean,
    val crlf: Boolean,
    val trimUnquoted: Boolean
) {
    companion object {
        fun of(config: DelimitedReadConfig): SyntaxSpec {
            require(config.dialect.delimiter.length == 1) { "Delimiter must contain exactly one character" }
            val quote = config.dialect.quote?.also {
                require(it.length == 1) { "Quote must contain exactly one character when enabled" }
            }?.single()
            require(quote == null || quote != config.dialect.delimiter.single()) {
                "Delimiter and quote must differ"
            }
            require(config.dialect.emptyField == "empty") {
                "Unsupported empty-field policy '${config.dialect.emptyField}'"
            }
            return SyntaxSpec(
                config.dialect.delimiter.single(), quote,
                when (config.dialect.escape) {
                    "double-quote" -> true
                    "none" -> false
                    else -> error("Unsupported escape convention '${config.dialect.escape}'")
                },
                when (config.framing.separator) {
                    "lf" -> false
                    "crlf" -> true
                    else -> error("Unsupported record separator '${config.framing.separator}'")
                },
                when (config.dialect.trimming) {
                    "unquoted" -> true
                    "none" -> false
                    else -> error("Unsupported trimming policy '${config.dialect.trimming}'")
                })
        }
    }
}


private data class Limits(val record: Int, val field: Int, val fields: Int) {
    companion object {
        fun of(policy: ReadOperationalPolicy): Limits = Limits(
            policy.maximumRecordCharacters ?: Int.MAX_VALUE,
            policy.maximumFieldCharacters ?: Int.MAX_VALUE,
            policy.maximumFields ?: Int.MAX_VALUE)
    }
}


private enum class ParserState { Start, Unquoted, Quoted, AfterQuote }


private class Parser(
    input: SequentialCharacterContent,
    private val syntax: SyntaxSpec,
    private val limits: Limits,
    private val context: DelimitedReadContext,
    private val checkpoint: () -> Unit
) {
    private val chars = CharacterInput(input)
    private var physicalRecordIndex = 0L

    fun readRecord(): RawRecord? {
        var state = ParserState.Start
        val fields = mutableListOf<RawField>()
        val field = StringBuilder()
        var fieldStart = chars.offset
        var fieldQuoted = false
        var recordCharacters = 0
        var sawInput = false

        fun budgetCharacter() {
            recordCharacters++
            if (recordCharacters > limits.record) budget("record-character", limits.record)
            if (recordCharacters and 1023 == 0) checkpoint()
        }
        fun append(character: Char) {
            field.append(character)
            if (field.length > limits.field) budget("field-character", limits.field)
        }
        fun commitField(endOffset: Long) {
            if (fields.size >= limits.fields) budget("field-count", limits.fields)
            val value = if (syntax.trimUnquoted && !fieldQuoted) field.toString().trim() else field.toString()
            fields.add(RawField(value, fieldStart..(endOffset - 1).coerceAtLeast(fieldStart)))
            field.setLength(0)
            fieldQuoted = false
            fieldStart = chars.offset
        }
        fun complete(endOffset: Long): RawRecord {
            commitField(endOffset)
            physicalRecordIndex++
            checkpoint()
            return RawRecord(fields)
        }

        while (true) {
            val next = chars.read()
            if (next < 0) {
                if (!sawInput) return null
                if (state == ParserState.Quoted) syntax("Unterminated quoted field", chars.offset)
                return complete(chars.offset)
            }
            val c = next.toChar()
            sawInput = true

            val separator = when {
                state == ParserState.Quoted -> false
                syntax.crlf && c == '\r' -> {
                    val following = chars.read()
                    if (following != '\n'.code) syntax("Bare CR or mixed record separator", chars.offset - 1)
                    true
                }
                syntax.crlf && c == '\n' -> syntax("Bare LF or mixed record separator", chars.offset - 1)
                !syntax.crlf && c == '\r' -> syntax("Bare CR or mixed record separator", chars.offset - 1)
                !syntax.crlf && c == '\n' -> true
                else -> false
            }
            if (separator) {
                if (state == ParserState.Quoted) append(c)
                else return complete(chars.offset - if (syntax.crlf) 2 else 1)
                continue
            }

            budgetCharacter()
            when (state) {
                ParserState.Start -> when {
                    c == syntax.delimiter -> commitField(chars.offset - 1)
                    syntax.quote != null && c == syntax.quote -> {
                        state = ParserState.Quoted
                        fieldQuoted = true
                        fieldStart = chars.offset
                    }
                    else -> { append(c); state = ParserState.Unquoted }
                }
                ParserState.Unquoted -> when {
                    c == syntax.delimiter -> { commitField(chars.offset - 1); state = ParserState.Start }
                    syntax.quote != null && c == syntax.quote -> syntax("Quote inside unquoted field", chars.offset - 1)
                    else -> append(c)
                }
                ParserState.Quoted -> if (c == syntax.quote) state = ParserState.AfterQuote else append(c)
                ParserState.AfterQuote -> when {
                    syntax.doubledQuote && c == syntax.quote -> { append(c); state = ParserState.Quoted }
                    c == syntax.delimiter -> { commitField(chars.offset - 1); state = ParserState.Start }
                    else -> syntax("Unexpected character after closing quote", chars.offset - 1)
                }
            }
        }
    }

    private fun syntax(detail: String, offset: Long): Nothing = throw DelimitedReadException(
        DelimitedReadException.syntax, context, physicalRecordIndex + 1,
        span = offset..offset, detail = detail)

    private fun budget(kind: String, limit: Int): Nothing = throw DelimitedReadException(
        DelimitedReadException.budget, context, physicalRecordIndex + 1,
        detail = "$kind limit $limit exceeded")
}


private class CharacterInput(private val input: SequentialCharacterContent) {
    private val buffer = CharArray(8192)
    private var size = 0
    private var index = 0
    var offset = 0L
        private set

    fun read(): Int {
        while (index >= size) {
            size = input.read(buffer)
            index = 0
            if (size < 0) return -1
            if (size == 0) continue
        }
        offset++
        return buffer[index++].code
    }
}


private data class DecodedField(val text: String, val isNull: Boolean)


internal fun validateDelimitedTypedConfig(
    contract: DataContract,
    policy: tech.kzen.auto.common.data.read.TypedDecodePolicy,
    context: DelimitedReadContext
) {
    TypedDecoder(contract, policy, context)
}


private class TypedDecoder(
    contract: DataContract,
    private val policy: tech.kzen.auto.common.data.read.TypedDecodePolicy,
    private val context: DelimitedReadContext
) {
    private val fields = (contract.structural as DataType.Record).fields
    private val overrides: Map<String, FieldDecodeOverride>

    init {
        require(policy.malformedValue == "fail-part") {
            "Only malformed-value policy 'fail-part' is implemented"
        }
        val entries = policy.fieldOverrides.map { override ->
            require(override.path.size == 1) { "Delimited field override path must name exactly one field" }
            override.path.single() to override
        }
        require(entries.map { it.first }.distinct().size == entries.size) { "Duplicate field decode override" }
        overrides = entries.toMap()
        val names = fields.map { it.id.name }.toSet()
        require(overrides.keys.all { it in names }) { "Decode override names an unknown field" }
        fields.forEach { field ->
            val scalar = field.type as? DataType.Scalar
                ?: invalid("Field '${field.id.name}' is not scalar")
            when (val kind = scalar.kind) {
                ScalarKind.Text, ScalarKind.Boolean, ScalarKind.Decimal,
                is ScalarKind.Floating -> Unit
                is ScalarKind.Integer -> {
                    require(kind.bits != null) {
                        "Field '${field.id.name}' must use a bounded integer kind"
                    }
                    require(kind.signed || kind.bits != 64) {
                        "Field '${field.id.name}' uses unsigned 64-bit integer, which cannot be read exactly"
                    }
                }
                else -> invalid("Field '${field.id.name}' uses unsupported kind $kind")
            }
        }
    }

    fun decode(index: Int, raw: RawField, recordIndex: Long): DecodedField {
        val field = fields[index]
        val scalar = field.type as DataType.Scalar
        val nullToken = overrides[field.id.name]?.nullToken ?: policy.nullToken
        if (nullToken != null && raw.text == nullToken) {
            if (!scalar.nullable) failure(recordIndex, field.id.name, raw.span, "Null token in non-nullable field")
            return DecodedField("", true)
        }
        val canonical = try {
            when (val kind = scalar.kind) {
                ScalarKind.Text -> raw.text
                ScalarKind.Boolean -> when (raw.text) {
                    "true", "false" -> raw.text
                    else -> error("Boolean must be 'true' or 'false'")
                }
                ScalarKind.Decimal -> BigDecimal(raw.text).stripTrailingZeros().let {
                    if (it.signum() == 0) "0" else it.toString()
                }
                is ScalarKind.Floating -> if (kind.bits == 32) {
                    raw.text.toFloat().also { require(it.isFinite()) }.toString()
                } else {
                    raw.text.toDouble().also { require(it.isFinite()) }.toString()
                }
                is ScalarKind.Integer -> canonicalInteger(raw.text, kind)
                else -> error("Unsupported kind $kind")
            }
        }
        catch (e: Exception) {
            failure(recordIndex, field.id.name, raw.span, "Malformed ${scalar.kind} value")
        }
        return DecodedField(canonical, false)
    }

    private fun canonicalInteger(text: String, kind: ScalarKind.Integer): String {
        val value = BigInteger(text)
        val bits = kind.bits!!
        val minimum = if (kind.signed) BigInteger.ONE.shiftLeft(bits - 1).negate() else BigInteger.ZERO
        val maximum = if (kind.signed) BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE)
            else BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)
        require(value in minimum..maximum)
        return value.toString()
    }

    private fun failure(record: Long, field: String, span: LongRange, detail: String): Nothing =
        throw DelimitedReadException(
            DelimitedReadException.typedValue, context, record, field, span, detail)

    private fun invalid(detail: String): Nothing = throw DelimitedReadException(
        DelimitedReadException.configuration, context, 0, detail = detail)
}
