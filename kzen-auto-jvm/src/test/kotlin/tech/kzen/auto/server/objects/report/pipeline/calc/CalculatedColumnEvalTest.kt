package tech.kzen.auto.server.objects.report.pipeline.calc

import org.junit.Test
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.EmbeddedKotlinCompiler
import tech.kzen.auto.server.util.WorkUtils
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.assertEquals


class CalculatedColumnEvalTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun twoPlusTwoIsFour() {
        testEval("4", "2 + 2")
    }


    @Test
    fun columnValueSum() {
        testEval("5", "a + b", "2", "3")
    }


    @Test
    fun numberPlusColumnSum() {
        testEval("5", "2 + a", "3")
        testEval("5", "2.0 + a", "3")
    }


    @Test
    fun columnPlusNumberSum() {
        testEval("5", "a + 2", "3")
        testEval("5", "a + 2.0", "3")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun twoTimesTwoIsFour() {
        testEval("4", "2 * 2")
    }


    @Test
    fun columnValueProduct() {
        testEval("6", "a * b", "2", "3")
    }


    @Test
    fun numberTimesColumn() {
        testEval("6", "2 * a", "3")
        testEval("6", "2.0 * a", "3")
    }


    @Test
    fun columnTimesNumber() {
        testEval("6", "a * 2", "3")
        testEval("6", "a * 2.0", "3")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun twoDivideByTwoIsOne() {
        testEval("1", "2 / 2")
    }


    @Test
    fun columnValueFraction() {
        testEval("1.5", "a / b", "3", "2")
    }


    @Test
    fun numberDivideByColumn() {
        testEval("2", "6 / a", "3")
        testEval("2", "6.0 / a", "3")
    }


    @Test
    fun columnDivideByNumber() {
        testEval("1.5", "a / 2", "3")
        testEval("1.5", "a / 2.0", "3")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun twoMinusTwoIsZero() {
        testEval("0", "2 - 2")
    }


    @Test
    fun columnValueSubtraction() {
        testEval("-1", "a - b", "2", "3")
    }


    @Test
    fun numberMinusColumn() {
        testEval("-1", "2 - a", "3")
        testEval("-1", "2.0 - a", "3")
    }


    @Test
    fun columnMinusNumber() {
        testEval("1", "a - 2", "3")
        testEval("1", "a - 2.0", "3")
    }


    @Test
    fun columnMinusError() {
        testEval("<error>", "a - 2", "foo")
        testEval("<error>", "a - 2.0", "foo")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun literalTextConcat() {
        testEval("22", """ "2" + "2" """)
    }


    @Test
    fun literalColumnConcat() {
        testEval("22", """ "2" + a """, "2")
    }


    @Test
    fun columnLiteralConcat() {
        testEval("22", """ a + "2" """, "2")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun columnNumberConcat() {
        testEval("foo2", "a + 2", "foo")
        testEval("foo2.0", "a + 2.0", "foo")
        testEval("foo2.0", "a + 2.00", "foo")
    }


    @Test
    fun numberColumnConcat() {
        testEval("2foo", "2 + a", "foo")
        testEval("2.0foo", "2.0 + a", "foo")
        testEval("2foo", """ "2${'$'}a"""", "foo")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun columnTextConcat() {
        testEval("foo2", """a + "2" """, "foo")
    }


    @Test
    fun textColumnConcat() {
        testEval("foobar", """ "foo" + a""", "bar")
    }


    @Test
    fun textContains() {
        testEval("true", """a.contains("foo")""", "foo")
    }


    @Test
    fun textNotContains() {
        testEval("false", """a.contains("foo")""", "bar")
    }


    @Test
    fun textEquals() {
        testEval("true", """a eq "foo" """, "foo")
    }


    @Test
    fun equalsText() {
        testEval("true", """ "foo" eq a""", "foo")
    }


    @Test
    fun equalsNumber() {
        testEval("true", """a eq 3""", "3")
        testEval("true", """a eq 3.0""", "3")
    }


    @Test
    fun numberEquals() {
        testEval("true", """3 eq a""", "3")
        testEval("true", """3.0 eq a""", "3")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unaryPlusPositiveNumber() {
        testEval("2", "+a", "2")
    }


    @Test
    fun unaryPlusNegativeNumber() {
        testEval("-2", "+a", "-2")
    }


    @Test
    fun unaryMinusPositiveNumber() {
        testEval("-2", "-a", "2")
    }


    @Test
    fun unaryMinusNegativeNumber() {
        testEval("2", "-a", "-2")
    }


    @Test
    fun unaryMinusZero() {
        testEval("0", "-a", "0")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyFalse() {
        testEval("false", "a.truthy", "")
    }


    @Test
    fun trueTrue() {
        testEval("true", "a.isTrue", "true")
    }


    @Test
    fun yLowerTrue() {
        testEval("true", "a.truthy", "y")
    }


    @Test
    fun yUpperTrue() {
        testEval("true", "a.truthy", "Y")
    }


    @Test
    fun oneTrue() {
        testEval("true", "a.truthy", "1")
    }


    @Test
    fun zeroFalse() {
        testEval("false", "a.truthy", "0")
    }


    @Test
    fun fooFalse() {
        testEval("false", "a.truthy", "foo")
    }


    @Test
    fun unaryNotTrue() {
        testEval("false", "!a", "true")
    }


    @Test
    fun unaryNotFalse() {
        testEval("true", "!a", "false")
    }


    @Test
    fun unaryNotMalformed() {
        testEval("<error>", "!a", "foo")
    }


    @Test
    fun ifElse() {
        testEval("1", "If(a, 1, 2)", "true")
        testEval("2", "a.If(1, 2)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun compareLiteralNumbers() {
        testEval("true", "2 < 3")
        testEval("false", "2 >= 3")
    }


    @Test
    fun compareLiteralText() {
        testEval("true", """ "2021-02-17" < "2021-02-18" """)
        testEval("false", """ "2021-02-17" >= "2021-02-18" """)
    }


    @Test
    fun compareColumnToColumnNumbers() {
        testEval("true", "a < b", "2", "3")
        testEval("false", "a >= b", "2", "3")
    }


    @Test
    fun compareColumnToColumnText() {
        testEval("true", "a < b", "2021-02-17", "2021-02-18")
        testEval("false", "a >= b", "2021-02-17", "2021-02-18")
    }


    @Test
    fun compareColumnToLiteralNumbers() {
        testEval("true", "a < 3", "2")
        testEval("false", "a >= 3", "2")
    }


    @Test
    fun compareColumnToLiteralText() {
        testEval("true", """a < "2021-02-18" """, "2021-02-17")
        testEval("false", """a >= "2021-02-18" """, "2021-02-17")
    }


    @Test
    fun compareLiteralToColumnNumbers() {
        testEval("true", "2 < a", "3")
        testEval("true", "2.0 < a", "3")
        testEval("false", "2 >= a", "3")
        testEval("false", "2.0 >= a", "3")
    }


    @Test
    fun compareLiteralToColumnText() {
        testEval("true", """ "2021-02-17" < a""", "2021-02-18")
        testEval("false", """ "2021-02-17" >= a""", "2021-02-18")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun testEval(
        expected: String,
        formula: String,
        aValue: String = "",
        bValue: String = ""
    ) {
        val calculatedColumnEval = calculatedColumnEval()

        val columnNames = listOf("a", "b")
        val calculatedColumn = calculatedColumnEval.create(
            "c",
            formula,
            HeaderListing(columnNames))

        val value = calculatedColumn.evaluate(
            RecordRowBuffer.of(aValue, bValue),
            RecordHeader.of(columnNames))

        assertEquals(expected, value)
    }


    @OptIn(ExperimentalPathApi::class)
    private fun calculatedColumnEval(): CalculatedColumnEval {
        val kotlinCompiler = EmbeddedKotlinCompiler()
        val workUtils = WorkUtils.temporary("CachedKotlinCompiler")
        val reportWorkPool = ReportWorkPool(workUtils)
        val cachedKotlinCompiler = CachedKotlinCompiler(kotlinCompiler, reportWorkPool, workUtils)
        return CalculatedColumnEval(cachedKotlinCompiler)
    }
}