package tech.kzen.auto.server.objects.report.pipeline.input.model

import org.junit.Test
import kotlin.test.assertEquals


class RecordFieldFlyweightTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val flyweight = RecordFieldFlyweight()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun simpleDecimalNotExact() {
        checkToDouble("10.1")
    }


    @Test
    fun simpleDecimalPiApproximation() {
        checkToDouble("3.14")
    }


    @Test
    fun simpleDecimalDollars() {
        checkToDouble("82.88")
    }


    @Test
    fun decimalWithLeadingZeroesAfterPoint() {
        checkToDouble("0.0014218039999999998")
    }


    @Test
    fun decimalWithLeadingZeroesBeforePoint() {
        checkToDouble("000000000000000000000000000000000010.1")
    }


    @Test
    fun negativeDecimalWithoutLeadingZero() {
        checkToDouble("-.567675")
    }


    @Test
    fun positiveDecimalWithoutLeadingZero() {
        checkToDouble(".810925")
    }


    @Test
    fun negativeDecimal() {
        checkToDouble("-3.14")
    }


    @Test
    fun positiveZero() {
        val values = listOf("0", "0.0", ".0", "+0", "+0.0", "+.0", "000000", "0.0000", "0000.00000")
        for (value in values) {
            checkToDouble(value)
        }
    }


    @Test
    fun negativeZero() {
        val values = listOf("-0", "-0.0", "-.0", "-000000", "-0.0000", "-0000.00000")
        for (value in values) {
            checkToDouble(value, 0.0)
        }
    }


    private fun checkToDouble(value: String) {
        checkToDouble(value, value.toDouble())
    }


    private fun checkToDouble(value: String, expected: Double) {
        val record = FlatFileRecord.of(value)
        flyweight.selectHostField(record, 0)
        assertEquals(expected, flyweight.toDoubleOrNan())
    }
}