package tech.kzen.auto.server.objects.report.pipeline.input.parse

import org.junit.Test
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTextFlyweight
import kotlin.test.assertEquals


class RecordTextFlyweightTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val flyweight = RecordTextFlyweight()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun simpleDecimalNotExact() {
        val csv = "10.1"
        val record = RecordItemBuffer.of(csv)
        flyweight.selectHostField(record, 0)
        assertEquals(csv.toDouble(), flyweight.toDoubleOrNan())
        assertEquals(csv.toDouble(), flyweight.toDouble())
    }


    @Test
    fun simpleDecimalPiApproximation() {
        val csv = "3.14"
        val record = RecordItemBuffer.of(csv)
        flyweight.selectHostField(record, 0)
        assertEquals(csv.toDouble(), flyweight.toDoubleOrNan())
        assertEquals(csv.toDouble(), flyweight.toDouble())
    }


    @Test
    fun simpleDecimalDollars() {
        val csv = "82.88"
        val record = RecordItemBuffer.of(csv)
        flyweight.selectHostField(record, 0)
        assertEquals(csv.toDouble(), flyweight.toDoubleOrNan())
        assertEquals(csv.toDouble(), flyweight.toDouble())
    }


    @Test
    fun decimalWithLeadingZeroesAfterPoint() {
        val csv = "0.0014218039999999998"
        val record = RecordItemBuffer.of(csv)
        flyweight.selectHostField(record, 0)
        assertEquals(csv.toDouble(), flyweight.toDoubleOrNan())
        assertEquals(csv.toDouble(), flyweight.toDouble())
    }


    @Test
    fun decimalWithLeadingZeroesBeforePoint() {
        val csv = "000000000000000000000000000000000010.1"
        val record = RecordItemBuffer.of(csv)
        flyweight.selectHostField(record, 0)
        assertEquals(csv.toDouble(), flyweight.toDoubleOrNan())
        assertEquals(csv.toDouble(), flyweight.toDouble())
    }


    @Test
    fun negativeDecimalWithoutLeadingZero() {
        val csv = "-.567675"
        val record = RecordItemBuffer.of(csv)
        flyweight.selectHostField(record, 0)
        assertEquals(csv.toDouble(), flyweight.toDoubleOrNan())
        assertEquals(csv.toDouble(), flyweight.toDouble())
    }


    @Test
    fun positiveDecimalWithoutLeadingZero() {
        val csv = ".810925"
        val record = RecordItemBuffer.of(csv)
        flyweight.selectHostField(record, 0)
        assertEquals(csv.toDouble(), flyweight.toDoubleOrNan())
        assertEquals(csv.toDouble(), flyweight.toDouble())
    }


    @Test
    fun negativeDecimal() {
        val csv = "-3.14"
        val record = RecordItemBuffer.of(csv)
        flyweight.selectHostField(record, 0)
        assertEquals(csv.toDouble(), flyweight.toDoubleOrNan())
        assertEquals(csv.toDouble(), flyweight.toDouble())
    }
}