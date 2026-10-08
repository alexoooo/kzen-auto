package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.DelimitedDialectSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.common.data.read.TypedDecodePolicy
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


/**
 * A failure names its record, column and character span exactly: spans are zero-based character offsets into the
 * input, a quoted field's span starts after its opening quote, and a trimmed field's covers its untrimmed text.
 */
class ConfiguredDelimitedReaderPositionTest {
    private val source = "memory://positions"
    private val malformed = "Malformed Integer(bits=8, signed=true) value"
    private val numbers = contract(
        "a" to DataType.Scalar(ScalarKind.Integer(8)),
        "b" to DataType.Scalar(ScalarKind.Integer(8)),
        "c" to DataType.Scalar(ScalarKind.Text))


    @Test
    fun typedFailuresNameTheFieldSpan() {
        val cases = listOf(
            Triple("1;300;x", config(numbers), "record 1, field b, span 2..4: $malformed"),
            Triple("1;\"300\";x", config(numbers), "record 1, field b, span 3..6: $malformed"),
            Triple("1;  300  ;x", config(numbers), "record 1, field b, span 2..8: $malformed"),
            Triple("1;2;x\n3;300;y", config(numbers), "record 2, field b, span 8..10: $malformed"),
            Triple("1;2;x\r\n3;300;y\r\n", config(numbers, separator = "crlf"),
                "record 2, field b, span 9..11: $malformed"),
            Triple("1;;x", config(numbers), "record 1, field b, span 2..2: $malformed"),
            Triple("1;2;x\n3;NULL;y", config(numbers, nullToken = "NULL"),
                "record 2, field b, span 8..11: Null token in non-nullable field"),
            Triple("skip\n# note\n1;2;x\n# note\n300;1;y",
                config(numbers, skipLeadingLines = 1, commentPrefix = "#"),
                "record 2, field a, span 25..27: $malformed"),
            Triple("a;b;c\n1;2;x\n4;5;y\n7;300;z", config(numbers, header = "present"),
                "record 3, field b, span 20..22: $malformed"),
            Triple("1;2;300", config(contract(
                "a" to DataType.Scalar(ScalarKind.Integer(8)),
                "b" to DataType.Scalar(ScalarKind.Integer(8)),
                "c" to DataType.Scalar(ScalarKind.Integer(8)))),
                "record 1, field c, span 4..6: $malformed"))
        for ((input, config, expected) in cases) {
            assertEquals("typed-value at $source, $expected", failure(input, config).message, input)
        }
    }


    @Test
    fun syntaxAndWidthFailuresNameTheirPosition() {
        val text = config(contract(
            "a" to DataType.Scalar(ScalarKind.Text),
            "b" to DataType.Scalar(ScalarKind.Text)))
        val cases = listOf(
            Triple("x;y\nab\"c;d", text, "record-syntax at $source, record 2, span 6..6: Quote inside unquoted field"),
            Triple("x;y\n\"ab\"c;d", text,
                "record-syntax at $source, record 2, span 8..8: Unexpected character after closing quote"),
            Triple("x;y\na\rb;c", text,
                "record-syntax at $source, record 2, span 5..5: Bare CR or mixed record separator"),
            Triple("x;y\na;\"open", text,
                "record-syntax at $source, record 2, span 11..11: Unterminated quoted field"),
            Triple("x;y\na;b;c", text, "record-width at $source, record 2, column 3: Expected 2 fields but found 3"),
            Triple("x;y\na", text, "record-width at $source, record 2, column 2: Expected 2 fields but found 1"),
            Triple("x;y\r\na;b\nc;d", config(contract(
                "a" to DataType.Scalar(ScalarKind.Text),
                "b" to DataType.Scalar(ScalarKind.Text)), separator = "crlf"),
                "record-syntax at $source, record 2, span 8..8: Bare LF or mixed record separator"))
        for ((input, config, expected) in cases) {
            assertEquals(expected, failure(input, config).message, input)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun failure(input: String, config: DelimitedReadConfig): DelimitedReadException =
        assertFailsWith<DelimitedReadException> {
            ConfiguredDelimitedReader.open(
                StringContent(input), config, ReadOperationalPolicy(), DelimitedReadContext(source)
            ).use { reader ->
                while (reader.read() != null) {
                    continue
                }
            }
        }


    private fun config(
        schema: DataContract,
        header: String = "absent",
        separator: String = "lf",
        nullToken: String? = null,
        skipLeadingLines: Int = 0,
        commentPrefix: String? = null
    ) = DelimitedReadConfig(
        RecordFramingSpec(separator),
        DelimitedDialectSpec(";", "\"", "double-quote", "empty", "unquoted"),
        HeaderReadSpec(header, "exact-name"),
        CharacterDecodingSpec("UTF-8", "forbid", "report", "report"),
        schema,
        TypedDecodePolicy(nullToken, "fail-part", emptyList()),
        skipLeadingLines,
        commentPrefix)


    private fun contract(vararg fields: Pair<String, DataType>): DataContract = DataContract(
        DataType.Record(fields.map { (name, type) -> DataField(FieldId(name, 0), type) }))


    private class StringContent(private val text: String): SequentialCharacterContent {
        override val resolvedCharsetName = "UTF-8"
        override val inspectionRecordLimit = Long.MAX_VALUE
        private var position = 0

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (position >= text.length) return -1
            val count = minOf(length, text.length - position)
            text.toCharArray(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun close() {}
    }
}
