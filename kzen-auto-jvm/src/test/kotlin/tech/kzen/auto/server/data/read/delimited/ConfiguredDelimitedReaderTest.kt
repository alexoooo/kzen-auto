package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.DelimitedDialectSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.FieldDecodeOverride
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.common.data.read.TypedDecodePolicy
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import tech.kzen.auto.server.objects.job.worker.data.DataReadCore
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue


class ConfiguredDelimitedReaderTest {
    @Test
    fun framingMatrixKeepsQuotedContentAndFinalRecord() {
        val input = "alpha;1.5\n\"beta;two\";2.75\n\"multi\nline\";\"say \"\"hi\"\"\""
        reader(input, config(contract(
            "key" to scalar(ScalarKind.Text),
            "value" to scalar(ScalarKind.Text)))).use {
            assertEquals(listOf("alpha", "1.5"), it.read()!!.backing.toList())
            assertEquals(listOf("beta;two", "2.75"), it.read()!!.backing.toList())
            assertEquals(listOf("multi\nline", "say \"hi\""), it.read()!!.backing.toList())
            assertNull(it.read())
        }
    }

    @Test
    fun emptyInputTrimmingAndLineEndingsAreDeterministic() {
        reader("", config(textContract(1))).use { assertNull(it.read()) }
        reader("  a  ;\" b \"\n", config(textContract(2))).use {
            assertEquals(listOf("a", " b "), it.read()!!.backing.toList())
            assertNull(it.read())
        }
        reader("a;b\r\nc;d\r\n", config(textContract(2), separator = "crlf")).use {
            assertEquals(listOf("a", "b"), it.read()!!.backing.toList())
            assertEquals(listOf("c", "d"), it.read()!!.backing.toList())
            assertNull(it.read())
        }
    }

    @Test
    fun malformedSyntaxIsItsOwnCategory() {
        val cases = listOf(
            "\"unterminated" to "Unterminated",
            "bad\"quote;x" to "Quote inside",
            "\"closed\"x;y" to "after closing",
            "a\rb" to "Bare CR")
        for ((input, detail) in cases) {
            val failure = assertFailsWith<DelimitedReadException> {
                reader(input, config(textContract(if (';' in input) 2 else 1))).use { it.read() }
            }
            assertEquals(DelimitedReadException.syntax, failure.category)
            assertTrue(failure.message!!.contains(detail))
        }
        assertFailsWith<DelimitedReadException> {
            reader("a;b\nc;d", config(textContract(2), separator = "crlf")).use { it.read() }
        }
    }

    @Test
    fun headerMappingReordersAndNamesSetFailures() {
        val declared = contract(
            "name" to scalar(ScalarKind.Text),
            "amount" to scalar(ScalarKind.Integer(32)))
        reader("amount;name\n12;alice", config(declared, header = "present")).use {
            assertEquals(listOf("alice", "12"), it.read()!!.backing.toList())
        }
        for ((header, expected) in listOf("name;name\nx;y" to "Duplicate", "name;extra\nx;y" to "Missing")) {
            val failure = assertFailsWith<DelimitedReadException> {
                reader(header, config(declared, header = "present"))
            }
            assertEquals(DelimitedReadException.header, failure.category)
            assertTrue(failure.message!!.contains(expected))
        }
    }


    @Test
    fun leadingLinesAndCommentsAreAppliedBeforeTheHeader() {
        val configured = config(
            null,
            header = "present",
            skipLeadingLines = 1,
            commentPrefix = "#")
        reader("preamble\n# generated\nname;value\na;1\n# ignored\nb;2", configured).use {
            assertEquals(listOf("name", "value"),
                (it.contract.structural as DataType.Record).fields.map { field -> field.id.name })
            assertEquals(listOf("a", "1"), it.read()!!.backing.toList())
            assertEquals(listOf("b", "2"), it.read()!!.backing.toList())
            assertNull(it.read())
            assertEquals(1, it.skippedLeadingLines)
            assertEquals(2, it.skippedComments)
        }
    }


    @Test
    fun commentPrefixOnlyMatchesAtALogicalRecordBoundary() {
        val configured = config(textContract(2), commentPrefix = "#")
        reader("a;#value\n\"#quoted\";x\n## comment\nb;y", configured).use {
            assertEquals(listOf("a", "#value"), it.read()!!.backing.toList())
            assertEquals(listOf("#quoted", "x"), it.read()!!.backing.toList())
            assertEquals(listOf("b", "y"), it.read()!!.backing.toList())
            assertNull(it.read())
            assertEquals(1, it.skippedComments)
        }
    }


    @Test
    fun emptyHeaderReportsItsOneBasedColumnBeforeFieldConstruction() {
        val failure = assertFailsWith<DelimitedReadException> {
            reader("name;;value\na;b;c", config(null, header = "present"))
        }
        assertEquals(DelimitedReadException.header, failure.category)
        assertEquals(1, failure.recordIndex)
        assertEquals(2, failure.columnIndex)
        assertTrue(failure.message!!.contains("empty column name"))
    }

    @Test
    fun inferredLabelsKeepFirstRecord() {
        reader("a;b\nc;d", config(null, header = "infer-labels")).use {
            assertEquals(listOf("c0", "c1"),
                (it.contract.structural as DataType.Record).fields.map { field -> field.id.name })
            assertEquals(listOf("a", "b"), it.read()!!.backing.toList())
        }
    }

    @Test
    fun typedDecodePreservesDecimalAndNullState() {
        val declared = contract(
            "amount" to scalar(ScalarKind.Decimal),
            "count" to scalar(ScalarKind.Integer(8)),
            "enabled" to scalar(ScalarKind.Boolean, nullable = true))
        reader("9007199254740993.1000;127;NULL", config(declared, nullToken = "NULL")).use {
            val record = it.read()!!
            assertEquals(listOf("9007199254740993.1", "127", ""), record.backing.toList())
            val enabled = record.access.field(record.value.root, FieldId("enabled", 0))
            assertEquals(DataState.Null, record.access.state(enabled))
        }
    }


    @Test
    fun decimalCanonicalizationKeepsExtremeExponentBounded() {
        val declared = contract("amount" to scalar(ScalarKind.Decimal))
        reader("1e-1000000", config(declared)).use {
            assertEquals("1E-1000000", it.read()!!.backing.toList().single())
        }
    }


    @Test
    fun configuredTypedRecordCrossesReadCoreWithExactDecimalAndNullState() {
        val declared = contract(
            "amount" to scalar(ScalarKind.Decimal),
            "discount" to scalar(ScalarKind.Decimal, nullable = true))
        reader("9007199254740993.1000;NULL", config(declared, nullToken = "NULL")).use { parser ->
            val parsed = parser.read()!!
            val base = LegacyDataShapeBridge.tabular(
                HeaderListing.ofUnique(listOf("amount", "discount")))
            val cursorShape = DataShape(
                declared, base.provenance, base.stability, base.diagnostics)
            val attributes = linkedMapOf("source" to "fixture")
            val effective = DataReadCore.effectiveShape(cursorShape, attributes, "configured fixture")

            val emitted = DataReadCore.message(cursorShape, effective, parsed.value, attributes)
            val amount = emitted.access.field(emitted.root, FieldId("amount"))
            val discount = emitted.access.field(emitted.root, FieldId("discount"))

            assertEquals(
                "9007199254740993.1",
                (emitted.access.scalar(amount) as TextExecutionValue).value)
            assertEquals(DataState.Null, emitted.access.state(discount))
            assertEquals(
                DataType.Scalar(ScalarKind.Decimal),
                emitted.access.contract(amount).structural)
        }
    }

    @Test
    fun overrideAndMalformedValueFailAtReaderBoundary() {
        val declared = contract(
            "left" to scalar(ScalarKind.Integer(8), nullable = true),
            "right" to scalar(ScalarKind.Integer(8), nullable = true))
        val configured = config(declared,
            overrides = listOf(FieldDecodeOverride(listOf("right"), "NA")))
        reader("1;NA", configured).use {
            val record = it.read()!!
            val right = record.access.field(record.value.root, FieldId("right", 0))
            assertEquals(DataState.Null, record.access.state(right))
        }
        val failure = assertFailsWith<DelimitedReadException> {
            reader("128;1", configured).use { it.read() }
        }
        assertEquals(DelimitedReadException.typedValue, failure.category)
        assertEquals("left", failure.fieldPath)
        assertEquals(1L, failure.recordIndex)
    }

    @Test
    fun widthAndAllParserBudgetsFailWithLimits() {
        val configured = config(textContract(2))
        val width = assertFailsWith<DelimitedReadException> {
            reader("a;b;c", configured).use { it.read() }
        }
        assertEquals(DelimitedReadException.width, width.category)
        assertBudget("abcd;x", configured,
            ReadOperationalPolicy(maximumRecordCharacters = 3), "record-character")
        assertBudget("abcd;x", configured,
            ReadOperationalPolicy(maximumFieldCharacters = 3), "field-character")
        assertBudget("a;b;c", configured,
            ReadOperationalPolicy(maximumFields = 2), "field-count")
    }

    @Test
    fun cancellationIsCheckedInsideLongRecordsAndKindsResolveBeforeContent() {
        var checks = 0
        reader("a".repeat(2050) + ";x", config(textContract(2)),
            ReadOperationalPolicy(maximumRecordCharacters = 3000)) { checks++ }.use { it.read() }
        assertTrue(checks >= 3)
        assertFailsWith<DelimitedReadException> {
            reader("x", config(contract("when" to scalar(ScalarKind.Instant))))
        }
    }


    @Test
    fun cursorClosesInputExactlyOnceOnParserTypedAndCancellationFailures() {
        val parserContent = StringContent("bad\"quote;x\nnext;record")
        val parserCursor = cursor(parserContent, config(textContract(2)))
        assertFailsWith<DelimitedReadException> { parserCursor.hasNext() }
        assertEquals(1, parserContent.closeCount)
        parserCursor.close()
        assertEquals(1, parserContent.closeCount)

        val typedContent = StringContent("128;1\n2;3")
        val typedConfig = config(contract(
            "left" to scalar(ScalarKind.Integer(8)),
            "right" to scalar(ScalarKind.Integer(8))))
        val typedCursor = cursor(typedContent, typedConfig)
        assertFailsWith<DelimitedReadException> { typedCursor.hasNext() }
        assertEquals(1, typedContent.closeCount)
        typedCursor.close()
        assertEquals(1, typedContent.closeCount)

        val cancelledContent = StringContent("a".repeat(2050) + ";x")
        val cancelledCursor = cursor(
            cancelledContent,
            config(textContract(2)),
            ReadOperationalPolicy(maximumRecordCharacters = 3000)) {
                throw CancellationException("test cancellation")
            }
        assertFailsWith<CancellationException> { cancelledCursor.hasNext() }
        assertEquals(1, cancelledContent.closeCount)
        cancelledCursor.close()
        assertEquals(1, cancelledContent.closeCount)
    }

    private fun assertBudget(
        text: String,
        config: DelimitedReadConfig,
        policy: ReadOperationalPolicy,
        kind: String
    ) {
        val failure = assertFailsWith<DelimitedReadException> {
            reader(text, config, policy).use { it.read() }
        }
        assertEquals(DelimitedReadException.budget, failure.category)
        assertTrue(failure.message!!.contains(kind))
    }

    private fun reader(
        text: String,
        config: DelimitedReadConfig,
        policy: ReadOperationalPolicy = ReadOperationalPolicy(),
        checkpoint: () -> Unit = {}
    ) = ConfiguredDelimitedReader.open(
        StringContent(text), config, policy,
        DelimitedReadContext("memory://fixture", "unit-1", "main"), checkpoint)

    private fun cursor(
        content: StringContent,
        config: DelimitedReadConfig,
        policy: ReadOperationalPolicy = ReadOperationalPolicy(),
        checkpoint: () -> Unit = {}
    ): ConfiguredDelimitedDataCursor {
        val parser = ConfiguredDelimitedReader.open(
            content, config, policy,
            DelimitedReadContext("memory://fixture", "unit-1", "main"), checkpoint)
        return ConfiguredDelimitedDataCursor(
            parser,
            DataShape(parser.contract, ShapeProvenance.Declared, ShapeStability.Stable))
    }

    private fun config(
        schema: DataContract?,
        header: String = "absent",
        separator: String = "lf",
        nullToken: String? = null,
        overrides: List<FieldDecodeOverride> = emptyList(),
        skipLeadingLines: Int = 0,
        commentPrefix: String? = null
    ) = DelimitedReadConfig(
        RecordFramingSpec(separator),
        DelimitedDialectSpec(";", "\"", "double-quote", "empty", "unquoted"),
        HeaderReadSpec(header, "exact-name"),
        CharacterDecodingSpec("UTF-8", "forbid", "report", "report"),
        schema,
        TypedDecodePolicy(nullToken, "fail-part", overrides),
        skipLeadingLines,
        commentPrefix)

    private fun textContract(width: Int): DataContract = contract(*
        (1..width).map { "field-$it" to scalar(ScalarKind.Text) }.toTypedArray())

    private fun scalar(kind: ScalarKind, nullable: Boolean = false) = DataType.Scalar(kind, nullable)

    private fun contract(vararg fields: Pair<String, DataType>): DataContract = DataContract(
        DataType.Record(fields.map { (name, type) -> DataField(FieldId(name, 0), type) }))

    private class StringContent(private val text: String): SequentialCharacterContent {
        override val resolvedCharsetName = "UTF-8"
        override val inspectionRecordLimit = Long.MAX_VALUE
        var closeCount = 0
            private set
        private var position = 0
        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (position >= text.length) return -1
            val count = minOf(length, text.length - position)
            text.toCharArray(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun close() {
            closeCount++
        }
    }
}
