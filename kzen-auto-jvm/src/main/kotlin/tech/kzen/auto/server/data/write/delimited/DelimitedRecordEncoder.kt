package tech.kzen.auto.server.data.write.delimited

import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.server.data.write.RecordCells
import tech.kzen.auto.server.data.write.RecordEncoder
import tech.kzen.auto.server.data.write.RecordOutputState
import tech.kzen.lib.common.exec.data.type.DataType
import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder
import java.nio.charset.CodingErrorAction
import kotlin.math.ceil


/**
 * Writes records so that the configured delimited reader, given the same [config], reads them back as written; a
 * value that cannot be written that way fails by name.
 *
 * Quoting is minimal: a field is quoted when it contains the delimiter, the quote, CR or LF, when trimming would strip
 * its edge whitespace, or when it would make its line read as a comment or start with a byte-order mark. As in
 * commons-csv (which Report's `CsvExportFormatter` follows, byte for byte), an empty first field, a leading `#` and a
 * trailing character at or below space are also quoted when a quote is configured.
 *
 * A null cell is written as the null token, which reads back as null only in a nullable column of the format's
 * schema. A non-null value equal to the token fails: the reader matches the token against a field's text whether or
 * not it was quoted, so quoting cannot protect it.
 *
 * `malformed` and `contentCodings` only apply to reading. A `require` or `detect` BOM policy writes a byte-order
 * mark, since the reader then requires one. `skipLeadingLines` writes that many empty lines before the header, which
 * reading skips.
 */
class DelimitedRecordEncoder(
    private val title: String,
    private val columns: List<String>,
    config: DelimitedReadConfig
): RecordEncoder {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val byteOrderMark = '﻿'
        private const val commentMarker = '#'
        private val byteOrderMarkCharsets = setOf(Charsets.UTF_8, Charsets.UTF_16BE, Charsets.UTF_16LE)
    }


    private enum class QuoteReason(val description: String) {
        Delimiter("contains the delimiter"),
        CarriageReturn("contains a carriage return"),
        LineFeed("contains a line feed"),
        EdgeWhitespace("starts or ends with whitespace, which reading trims"),
        ByteOrderMark("starts with a byte-order mark, which reading drops")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val delimiter: Char
    private val quoting: Boolean
    private val quote: Char
    private val doubledQuote: Boolean
    private val crlf: Boolean
    private val trimUnquoted: Boolean
    private val commentPrefix: String?
    private val writesHeader: Boolean
    private val writesByteOrderMark: Boolean
    private val leadingLines: Int
    private val hasSchema: Boolean
    private val nullTokens: Array<String?>
    private val nullable: BooleanArray

    private val charset: Charset
    private val encoder: CharsetEncoder
    private val maxBytesPerChar: Double

    private val staging = DataRecordBuffer()
    private val fieldStarts = IntArray(columns.size)

    private val headerCells = object: RecordCells {
        override val size get() = columns.size
        override fun isNull(index: Int) = false
        override fun text(index: Int): CharSequence = columns[index]
    }


    init {
        ConfiguredDelimitedReaderCapability.validate(config)
        require(columns.isNotEmpty()) { "$title: a delimited record needs at least one column" }

        delimiter = config.dialect.delimiter.single()
        val configuredQuote = config.dialect.quote?.single()
        quoting = configuredQuote != null
        quote = configuredQuote ?: delimiter
        doubledQuote = config.dialect.escape == "double-quote"
        crlf = config.framing.separator == "crlf"
        trimUnquoted = config.dialect.trimming == "unquoted"
        commentPrefix = config.commentPrefix
        writesHeader = config.header.policy == "present"
        leadingLines = config.skipLeadingLines

        if (writesHeader) {
            require(columns.none(String::isEmpty)) { "$title: a header cannot hold an empty column name" }
            val duplicates = columns.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
            require(duplicates.isEmpty()) { "$title: a header cannot repeat column names: ${duplicates.joinToString()}" }
        }

        val schema = config.schema
        hasSchema = schema != null
        val tokens = config.typedDecode.fieldOverrides.associate { it.path.single() to it.nullToken }
        if (schema == null) {
            nullTokens = Array(columns.size) { config.typedDecode.nullToken }
            nullable = BooleanArray(columns.size)
        }
        else {
            val fields = (schema.structural as DataType.Record).fields
            val names = fields.map { it.id.name }
            // A header is matched to the schema by name; without one, by position
            val matches = if (writesHeader) columns.toSet() == names.toSet() else columns == names
            require(matches) {
                "$title: columns ${columns.joinToString()} do not match its schema ${names.joinToString()}"
            }
            val byName = fields.associateBy { it.id.name }
            nullTokens = Array(columns.size) { tokens[columns[it]] ?: config.typedDecode.nullToken }
            nullable = BooleanArray(columns.size) { byName.getValue(columns[it]).type.nullable }
        }

        charset = Charset.forName(config.characters.charset)
        encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(
                if (config.characters.unmappable.lowercase() == "replace") CodingErrorAction.REPLACE
                else CodingErrorAction.REPORT)
        maxBytesPerChar = encoder.maxBytesPerChar().toDouble()

        writesByteOrderMark = config.characters.bom.lowercase() in setOf("require", "detect")
        require(!writesByteOrderMark || charset in byteOrderMarkCharsets) {
            "$title: a byte-order mark cannot be written in ${charset.name()}"
        }

        val structural = listOfNotNull(
            delimiter, configuredQuote, '\r', '\n', byteOrderMark.takeIf { writesByteOrderMark })
        for (character in structural) {
            require(encoder.canEncode(character)) {
                "$title: ${charset.name()} cannot encode the format's own '${describe(character)}'"
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun openOutput(): RecordOutputState =
        RecordOutputState.Stateless


    override fun encodeHeader(state: RecordOutputState, output: DataRecordBuffer) {
        staging.charsLength = 0
        if (writesByteOrderMark) {
            reserve(1)
            staging.chars[staging.charsLength++] = byteOrderMark
        }
        repeat(leadingLines) {
            appendLineEnd()
        }
        if (writesHeader) {
            appendLine(headerCells, staging.charsLength, true)
        }
        if (staging.charsLength > 0) {
            encodeStaging(output, true)
        }
    }


    override fun encodeRecord(state: RecordOutputState, cells: RecordCells, output: DataRecordBuffer) {
        require(cells.size == columns.size) { "$title: expected ${columns.size} cells but found ${cells.size}" }
        staging.charsLength = 0
        appendLine(cells, 0, false)
        encodeStaging(output, false)
    }


    // Every line ends itself, so an output needs nothing after its last
    override fun encodeFooter(state: RecordOutputState, output: DataRecordBuffer) {}


    //-----------------------------------------------------------------------------------------------------------------
    private fun appendLine(cells: RecordCells, lineStart: Int, header: Boolean) {
        appendFields(cells, header, false)
        val prefix = commentPrefix
            ?: return
        if (!readsAsComment(prefix, lineStart)) {
            return
        }
        if (quoting && staging.chars[lineStart] != quote) {
            staging.charsLength = lineStart
            appendFields(cells, header, true)
        }
        require(!readsAsComment(prefix, lineStart)) {
            "$title: ${subject(0, header)} would make its line read as a comment ('$prefix')" +
                if (quoting) "" else ", and the format has no quote to prevent it"
        }
    }


    private fun appendFields(cells: RecordCells, header: Boolean, quoteFirst: Boolean) {
        for (index in columns.indices) {
            if (index > 0) {
                reserve(1)
                staging.chars[staging.charsLength++] = delimiter
            }
            fieldStarts[index] = staging.charsLength
            val forceQuote = quoteFirst && index == 0
            if (!header && cells.isNull(index)) {
                appendField(nullToken(index), index, header, forceQuote)
            }
            else {
                val text = cells.text(index)
                if (!header) {
                    requireNotNullToken(text, index)
                }
                appendField(text, index, header, forceQuote)
            }
        }
        appendLineEnd()
    }


    private fun appendLineEnd() {
        reserve(2)
        if (crlf) {
            staging.chars[staging.charsLength++] = '\r'
        }
        staging.chars[staging.charsLength++] = '\n'
    }


    private fun appendField(text: CharSequence, index: Int, header: Boolean, forceQuote: Boolean) {
        val length = text.length
        var required: QuoteReason? = null
        var preferred = forceQuote
        var containsQuote = false
        if (length == 0) {
            preferred = preferred || index == 0
        }
        else {
            val head = text[0]
            val tail = text[length - 1]
            preferred = preferred || head == commentMarker || tail <= ' '
            if (trimUnquoted && (head.isWhitespace() || tail.isWhitespace())) {
                required = QuoteReason.EdgeWhitespace
            }
            if (index == 0 && !writesByteOrderMark && head == byteOrderMark) {
                required = QuoteReason.ByteOrderMark
            }
            for (i in 0 until length) {
                val c = text[i]
                when {
                    c == delimiter -> required = QuoteReason.Delimiter
                    c == '\r' -> required = QuoteReason.CarriageReturn
                    c == '\n' -> required = QuoteReason.LineFeed
                    quoting && c == quote -> containsQuote = true
                }
            }
        }

        require(!containsQuote || doubledQuote) {
            "$title: ${subject(index, header)} contains the quote '$quote', which escape 'none' cannot write"
        }
        val quoted = required != null || containsQuote || (preferred && quoting)
        require(!quoted || quoting) {
            "$title: ${subject(index, header)} ${required!!.description}, and the format has no quote to protect it"
        }

        reserve(2 * length + 2)
        val chars = staging.chars
        var next = staging.charsLength
        if (quoted) {
            chars[next++] = quote
            for (i in 0 until length) {
                val c = text[i]
                if (c == quote) {
                    chars[next++] = quote
                }
                chars[next++] = c
            }
            chars[next++] = quote
        }
        else {
            for (i in 0 until length) {
                chars[next++] = text[i]
            }
        }
        staging.charsLength = next
    }


    private fun nullToken(index: Int): String {
        val token = nullTokens[index]
        require(token != null) { "$title: column '${columns[index]}' is null, but the format has no null token" }
        require(nullable[index]) {
            if (hasSchema) {
                "$title: column '${columns[index]}' is null, but its schema does not allow null there"
            }
            else {
                "$title: column '${columns[index]}' is null, but the null token '$token' reads back as null " +
                    "only in a nullable column of a schema, and the format has none"
            }
        }
        return token
    }


    private fun requireNotNullToken(text: CharSequence, index: Int) {
        val token = nullTokens[index]
            ?: return
        require(!text.contentEquals(token)) {
            "$title: column '${columns[index]}' holds '$token', the format's null token, " +
                "so it would not read back as itself"
        }
    }


    private fun readsAsComment(prefix: String, lineStart: Int): Boolean {
        if (staging.charsLength - lineStart < prefix.length) {
            return false
        }
        for (i in prefix.indices) {
            if (staging.chars[lineStart + i] != prefix[i]) {
                return false
            }
        }
        return true
    }


    // One encoding pass over the staged characters, as Report's CharsetExportEncoder does
    private fun encodeStaging(output: DataRecordBuffer, header: Boolean) {
        val charsLength = staging.charsLength
        val start = output.bytesLength
        val limit = start + ceil(maxBytesPerChar * charsLength).toInt()
        if (limit > output.bytes.size) {
            output.ensureByteCapacity(maxOf(limit, 2 * output.bytes.size))
        }

        val source = staging.initializedCharBuffer(charsLength)
        val target = output.initializedByteBuffer(limit).position(start)
        encoder.reset()
        var result = encoder.encode(source, target, true)
        if (!result.isError) {
            result = encoder.flush(target)
        }
        if (result.isError) {
            throw IllegalArgumentException(unencodable(source.position(), result.isMalformed, header))
        }
        check(!result.isOverflow) { "Encoded record exceeds its reserved size" }
        output.bytesLength = target.position()
    }


    private fun unencodable(position: Int, malformed: Boolean, header: Boolean): String {
        var index = 0
        while (index + 1 < columns.size && fieldStarts[index + 1] <= position) {
            index++
        }
        val codePoint = Character.codePointAt(staging.chars, position, staging.charsLength)
        val unicode = "U+%04X".format(codePoint)
        val problem =
            if (malformed) "an unpaired surrogate ($unicode)"
            else "'${String(Character.toChars(codePoint))}' ($unicode), which ${charset.name()} cannot encode"
        return "$title: ${subject(index, header)} holds $problem"
    }


    private fun subject(index: Int, header: Boolean): String =
        if (header) "the name of column '${columns[index]}'" else "column '${columns[index]}'"


    private fun describe(character: Char): String = when (character) {
        '\r' -> "\\r"
        '\n' -> "\\n"
        '\t' -> "\\t"
        byteOrderMark -> "byte-order mark"
        else -> character.toString()
    }


    private fun reserve(extra: Int) {
        val required = staging.charsLength + extra
        if (required > staging.chars.size) {
            staging.ensureCharCapacity(maxOf(required, 2 * staging.chars.size))
        }
    }
}
