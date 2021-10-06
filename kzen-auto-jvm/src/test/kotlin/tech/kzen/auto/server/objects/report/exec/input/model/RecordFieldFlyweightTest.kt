package tech.kzen.auto.server.objects.report.exec.input.model

import org.junit.Test
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import kotlin.test.assertEquals


class RecordFieldFlyweightTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val flyweight =
        FlatFileRecordField()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyNan() {
        checkToDouble("", Double.NaN)
    }


    @Test
    fun corruptExponentNan() {
        checkToDouble("6E+53154")
    }


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


    @Test
    fun scientificNotationSmall() {
        checkToDouble("1.2E2", 120.0)
    }

    @Test
    fun scientificNotationBig() {
        checkToDouble("1.033811826923076E-4")
        checkToDouble("1.2572920405982926E-5")
        checkToDouble("3.632478632478633E-6")
        checkToDouble("6.843413435657128E-7")
        checkToDouble("2.89384079327889E-8")
        checkToDouble("6.245749074647463E-9")

        checkToDouble("5.461420422967089E-10")
        checkToDouble("1.2854501085642218E-13")

        checkToDouble("6.245749074647463E9")
        checkToDouble("6.245749074647463e+9")
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