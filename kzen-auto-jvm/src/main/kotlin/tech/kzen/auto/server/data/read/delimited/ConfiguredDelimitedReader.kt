package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.FieldDecodeOverride
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
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
    val skippedLeadingLines: Long get() = parser.skippedLeadingLines
    val skippedComments: Long get() = parser.skippedComments

    companion object {
        fun open(
            input: SequentialCharacterContent,
            config: DelimitedReadConfig,
            context: DelimitedReadContext,
            checkpoint: () -> Unit = {}
        ): ConfiguredDelimitedReader = openSample(input, config, context, true, checkpoint)


        internal fun openSample(
            input: SequentialCharacterContent,
            config: DelimitedReadConfig,
            context: DelimitedReadContext,
            completeFinalRecord: Boolean,
            checkpoint: () -> Unit = {}
        ): ConfiguredDelimitedReader {
            try {
                val parser = Parser(
                    input, SyntaxSpec.of(config), context,
                    config.skipLeadingLines, config.commentPrefix, completeFinalRecord, checkpoint)
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
            val labels = List(header.fieldCount) { header.text(it) }
            val emptyColumn = labels.indexOfFirst(String::isEmpty)
            if (emptyColumn >= 0) {
                throw DelimitedReadException(
                    DelimitedReadException.header, context, 1,
                    columnIndex = emptyColumn + 1,
                    detail = "The first row contains an empty column name")
            }
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
            val width = first?.fieldCount ?: 0
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

    // One per file: every row shares its header
    private val header = FlatRecordHeader(contract)


    /**
     * The next row, its cells copied out of the parser's reused buffer into a record of their own. A row
     * allocates that record, its null flags when it has a null, and its value. Its cells' hash and number caches fill
     * on first use ([FlatFileRecord.cachedHash]), which assumes one reader at a time: whatever hands a row to several
     * readers at once fills them first ([FlatFileRecord.populateCaches]).
     */
    fun read(): DelimitedRecord? {
        val raw = pending?.also { pending = null } ?: parser.readRecord() ?: return null
        logicalRecordIndex++
        if (raw.fieldCount != physicalByOutput.size) {
            throw DelimitedReadException(
                DelimitedReadException.width, context, logicalRecordIndex,
                columnIndex = minOf(raw.fieldCount, physicalByOutput.size) + 1,
                detail = "Expected ${physicalByOutput.size} fields but found ${raw.fieldCount}")
        }
        val backing = FlatFileRecord(raw.contentLength, physicalByOutput.size)
        var nulls: BooleanArray? = null
        for (outputIndex in physicalByOutput.indices) {
            val isNull = typed.decode(outputIndex, raw, physicalByOutput[outputIndex], logicalRecordIndex, backing)
            if (isNull) {
                val flags = nulls ?: BooleanArray(physicalByOutput.size)
                flags[outputIndex] = true
                nulls = flags
            }
        }
        backing.attachHeader(header)
        return DelimitedRecord(backing, nulls)
    }

    override fun close() = input.close()

    private data class ProjectionPlan(val contract: DataContract, val mapping: IntArray)
}


/**
 * The record [Parser] last read, overwritten by its next read: field characters back to back with their end
 * offsets (as [FlatFileRecord] lays them out), and each field's character span in the input, for error messages.
 */
private class RawRecord {
    companion object {
        private const val initialCharacters = 256
        private const val initialFields = 16
    }

    var content = CharArray(initialCharacters)
        private set
    var contentLength = 0
        private set
    var fieldCount = 0
        private set
    private var ends = IntArray(initialFields)

    // Where the field being read starts in [content]
    private var openStart = 0

    // Start and end offset of each field, in pairs
    private var spans = LongArray(2 * initialFields)


    fun clear() {
        contentLength = 0
        fieldCount = 0
        openStart = 0
    }

    fun append(character: Char) {
        if (contentLength == content.size) {
            content = content.copyOf(2 * content.size)
        }
        content[contentLength++] = character
    }

    /** Appends [length] characters of [source] from [offset]. */
    fun append(source: CharArray, offset: Int, length: Int) {
        val required = contentLength + length
        if (required > content.size) {
            content = content.copyOf(maxOf(required, 2 * content.size))
        }
        source.copyInto(content, contentLength, offset, offset + length)
        contentLength = required
    }

    /** The length of the field being read, not yet committed. */
    fun openFieldLength(): Int = contentLength - openStart

    /** Ends the field being read; trimming drops its leading and trailing whitespace, as `String.trim` does. */
    fun commit(trim: Boolean, spanStart: Long, spanEnd: Long) {
        if (trim) {
            trimOpenField()
        }
        if (fieldCount == ends.size) {
            ends = ends.copyOf(2 * ends.size)
            spans = spans.copyOf(2 * spans.size)
        }
        ends[fieldCount] = contentLength
        spans[2 * fieldCount] = spanStart
        spans[2 * fieldCount + 1] = spanEnd
        fieldCount++
        openStart = contentLength
    }

    private fun trimOpenField() {
        val start = openStart
        var from = start
        var to = contentLength
        while (from < to && content[from].isWhitespace()) from++
        while (to > from && content[to - 1].isWhitespace()) to--
        if (from > start) {
            content.copyInto(content, start, from, to)
        }
        contentLength = start + (to - from)
    }

    fun start(field: Int): Int = if (field == 0) 0 else ends[field - 1]
    fun length(field: Int): Int = ends[field] - start(field)
    fun text(field: Int): String = String(content, start(field), length(field))
    fun span(field: Int): LongRange = spans[2 * field]..spans[2 * field + 1]

    fun textEquals(field: Int, text: String): Boolean {
        val start = start(field)
        if (length(field) != text.length) return false
        for (i in text.indices) {
            if (content[start + i] != text[i]) return false
        }
        return true
    }
}


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


private enum class ParserState { Start, Unquoted, Quoted, AfterQuote }


private class Parser(
    input: SequentialCharacterContent,
    private val syntax: SyntaxSpec,
    private val context: DelimitedReadContext,
    private val leadingLines: Int,
    private val commentPrefix: String?,
    private val completeFinalRecord: Boolean,
    private val checkpoint: () -> Unit
) {
    private val chars = CharacterInput(input)
    private var physicalRecordIndex = 0L
    private var initialized = false
    var skippedLeadingLines = 0L
        private set
    var skippedComments = 0L
        private set

    // The record being read, and where in it: reused from one record to the next
    private val record = RawRecord()
    // The quote, or the delimiter again when there is none, so a run stops at either without a null check
    private val runQuote = syntax.quote ?: syntax.delimiter
    private var fieldStart = 0L
    private var fieldQuoted = false
    private var recordCharacters = 0


    /** The next record, in the buffer the following call overwrites; null at the end of the input. */
    fun readRecord(): RawRecord? {
        initialize()
        while (commentPrefix != null && chars.consumePrefix(commentPrefix)) {
            skipPhysicalLine()
            skippedComments++
        }
        record.clear()
        var state = ParserState.Start
        fieldStart = chars.offset
        fieldQuoted = false
        recordCharacters = 0
        var sawInput = false

        while (true) {
            if (state != ParserState.AfterQuote) {
                // The characters that only extend the field, taken at once; whatever ends the run, or would exceed a
                // limit, is read one at a time below
                val taken = chars.takeRun(
                    record, state == ParserState.Quoted, syntax.delimiter, runQuote, runLength())
                if (taken > 0) {
                    sawInput = true
                    if (state == ParserState.Start) state = ParserState.Unquoted
                    countRun(taken)
                }
            }

            val next = chars.read()
            if (next < 0) {
                if (!sawInput) return null
                if (!completeFinalRecord) return null
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

            countCharacter()
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

    private fun countCharacter() {
        recordCharacters++
        if (recordCharacters and 1023 == 0) checkpoint()
    }

    /** How many characters a run may take: up to the next checkpoint (each 1024 of the record). */
    private fun runLength(): Int =
        1024 - (recordCharacters and 1023)

    // A run within [runLength] ends at most at a checkpoint, which it then takes as [countCharacter] would
    private fun countRun(taken: Int) {
        recordCharacters += taken
        if (recordCharacters and 1023 == 0) checkpoint()
    }

    private fun append(character: Char) {
        record.append(character)
    }

    private fun commitField(endOffset: Long) {
        record.commit(
            syntax.trimUnquoted && !fieldQuoted,
            fieldStart,
            (endOffset - 1).coerceAtLeast(fieldStart))
        fieldQuoted = false
        fieldStart = chars.offset
    }

    private fun complete(endOffset: Long): RawRecord {
        commitField(endOffset)
        physicalRecordIndex++
        checkpoint()
        return record
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        repeat(leadingLines) {
            if (!skipPhysicalLine()) return
            skippedLeadingLines++
        }
    }

    private fun skipPhysicalLine(): Boolean {
        var sawInput = false
        while (true) {
            val next = chars.read()
            if (next < 0) return sawInput
            sawInput = true
            val c = next.toChar()
            when {
                syntax.crlf && c == '\r' -> {
                    val following = chars.read()
                    if (following != '\n'.code) syntax("Bare CR or mixed record separator", chars.offset - 1)
                    return true
                }
                syntax.crlf && c == '\n' -> syntax("Bare LF or mixed record separator", chars.offset - 1)
                !syntax.crlf && c == '\r' -> syntax("Bare CR or mixed record separator", chars.offset - 1)
                !syntax.crlf && c == '\n' -> return true
            }
            if (chars.offset and 1023 == 0L) checkpoint()
        }
    }

    private fun syntax(detail: String, offset: Long): Nothing = throw DelimitedReadException(
        DelimitedReadException.syntax, context, physicalRecordIndex + 1,
        span = offset..offset, detail = detail)
}


private class CharacterInput(private val input: SequentialCharacterContent) {
    private val buffer = CharArray(8192)
    private var size = 0
    private var index = 0
    private var sourceOffset = 0L
    var offset = 0L
        private set

    // Characters given back by a prefix that did not match, as a stack: the top is read next
    private var replayCodes = IntArray(0)
    private var replayOffsets = LongArray(0)
    private var replaySize = 0

    // The characters a prefix match has read so far, reused from one match to the next
    private var consumedCodes = IntArray(0)
    private var consumedOffsets = LongArray(0)


    fun read(): Int {
        if (replaySize > 0) {
            replaySize--
            offset = replayOffsets[replaySize] + 1
            return replayCodes[replaySize]
        }
        while (index >= size) {
            size = input.read(buffer)
            index = 0
            if (size < 0) return -1
            if (size == 0) continue
        }
        val code = buffer[index++].code
        offset = sourceOffset + 1
        sourceOffset++
        return code
    }

    /**
     * Appends to [record] the buffered characters before the first that ends a run, at most [limit] of them, and
     * returns how many: in a [quoted] field only [quote] ends one; otherwise also [delimiter], CR and LF. Takes
     * nothing while characters wait to be read again, or when the buffer is used up ([read] refills it).
     */
    fun takeRun(record: RawRecord, quoted: Boolean, delimiter: Char, quote: Char, limit: Int): Int {
        val from = index
        if (replaySize > 0 || from >= size) {
            return 0
        }
        val end = if (size - from > limit) from + limit else size
        var next = from
        if (quoted) {
            while (next < end && buffer[next] != quote) next++
        }
        else {
            while (next < end) {
                val c = buffer[next]
                if (c == delimiter || c == quote || c == '\r' || c == '\n') break
                next++
            }
        }
        val taken = next - from
        if (taken > 0) {
            record.append(buffer, from, taken)
            index = next
            sourceOffset += taken
            offset = sourceOffset
        }
        return taken
    }


    fun consumePrefix(prefix: String): Boolean {
        if (consumedCodes.size < prefix.length) {
            consumedCodes = IntArray(prefix.length)
            consumedOffsets = LongArray(prefix.length)
        }
        var consumed = 0
        for (expected in prefix) {
            val code = read()
            if (code < 0) {
                unread(consumed)
                return false
            }
            consumedCodes[consumed] = code
            consumedOffsets[consumed] = offset - 1
            consumed++
            if (code != expected.code) {
                unread(consumed)
                return false
            }
        }
        return true
    }

    private fun unread(consumed: Int) {
        if (replayCodes.size < replaySize + consumed) {
            val capacity = maxOf(2 * replayCodes.size, replaySize + consumed)
            replayCodes = replayCodes.copyOf(capacity)
            replayOffsets = replayOffsets.copyOf(capacity)
        }
        for (index in consumed - 1 downTo 0) {
            replayCodes[replaySize] = consumedCodes[index]
            replayOffsets[replaySize] = consumedOffsets[index]
            replaySize++
        }
        if (consumed > 0) offset = consumedOffsets[0]
    }
}


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
    private companion object {
        const val textMode = 0
        const val booleanMode = 1
        const val numberMode = 2
    }

    private val fields = (contract.structural as DataType.Record).fields
    private val overrides: Map<String, FieldDecodeOverride>
    private val scalars: List<DataType.Scalar>

    // Per field, read on every cell: classified once here rather than by comparing kinds per cell
    private val nullTokens: Array<String?>
    private val nullable: BooleanArray
    private val modes: IntArray

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
        scalars = fields.map { it.type as DataType.Scalar }
        nullTokens = Array(fields.size) { overrides[fields[it].id.name]?.nullToken ?: policy.nullToken }
        nullable = BooleanArray(fields.size) { scalars[it].nullable }
        modes = IntArray(fields.size) {
            when (scalars[it].kind) {
                ScalarKind.Text -> textMode
                ScalarKind.Boolean -> booleanMode
                else -> numberMode
            }
        }
    }

    /**
     * Appends field [index], read from field [physical] of [raw], to [target]: text as it is (a Boolean once
     * validated), a number in its canonical form. True when the field is null; its cell is then empty.
     */
    fun decode(index: Int, raw: RawRecord, physical: Int, recordIndex: Long, target: FlatFileRecord): Boolean {
        val nullToken = nullTokens[index]
        if (nullToken != null && raw.textEquals(physical, nullToken)) {
            if (!nullable[index]) {
                failure(recordIndex, fields[index].id.name, raw.span(physical), "Null token in non-nullable field")
            }
            target.add("")
            return true
        }
        when (modes[index]) {
            textMode ->
                target.add(raw.content, raw.start(physical), raw.length(physical))

            booleanMode -> {
                if (!raw.textEquals(physical, "true") && !raw.textEquals(physical, "false")) {
                    malformed(index, raw, physical, recordIndex)
                }
                target.add(raw.content, raw.start(physical), raw.length(physical))
            }

            else -> {
                val canonical = try {
                    canonicalNumber(raw.text(physical), scalars[index].kind)
                }
                catch (e: Exception) {
                    malformed(index, raw, physical, recordIndex)
                }
                target.add(canonical)
            }
        }
        return false
    }

    private fun canonicalNumber(text: String, kind: ScalarKind): String = when (kind) {
        ScalarKind.Decimal -> BigDecimal(text).stripTrailingZeros().let {
            if (it.signum() == 0) "0" else it.toString()
        }
        is ScalarKind.Floating -> if (kind.bits == 32) {
            text.toFloat().also { require(it.isFinite()) }.toString()
        } else {
            text.toDouble().also { require(it.isFinite()) }.toString()
        }
        is ScalarKind.Integer -> canonicalInteger(text, kind)
        else -> error("Unsupported kind $kind")
    }

    private fun malformed(index: Int, raw: RawRecord, physical: Int, recordIndex: Long): Nothing =
        failure(recordIndex, fields[index].id.name, raw.span(physical), "Malformed ${scalars[index].kind} value")

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
            DelimitedReadException.typedValue, context, record, field, span, detail = detail)

    private fun invalid(detail: String): Nothing = throw DelimitedReadException(
        DelimitedReadException.configuration, context, 0, detail = detail)
}
