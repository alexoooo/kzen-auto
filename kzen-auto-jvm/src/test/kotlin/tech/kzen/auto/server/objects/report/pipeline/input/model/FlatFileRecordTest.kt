package tech.kzen.auto.server.objects.report.pipeline.input.model

import org.junit.Test
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import java.text.DecimalFormat
import kotlin.random.Random
import kotlin.test.assertEquals


class FlatFileRecordTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun addLong() {
        testAddLong("0", 0)
        testAddLong("0", -0)
        testAddLong("1", 1)
        testAddLong("-1", -1)
        testAddLong("42", 42)
        testAddLong("-420", -420)
        testAddLong("1000000000", 1000000000)
        testAddLong("1000000000000", 1000000000000)
        testAddLong("-1000000000000", -1000000000000)
    }


    @Test
    fun addDouble() {
        testAddDouble("0", 0.0, 0)
        testAddDouble("0", -0.0, 0)
        testAddDouble("0.0", -0.0, 1)
        testAddDouble("1", 1.0, 0)
        testAddDouble("-1", -1.0, 0)
        testAddDouble("42", 42.0, 0)
        testAddDouble("-42.0", -42.0, 1)
        testAddDouble("100.100", 100.100, 3)
        testAddDouble("100.001", 100.001, 3)
        testAddDouble("-100.001", -100.0011, 3)
        testAddDouble("0.001", 0.0011, 3)
        testAddDouble("-0.001", -0.0011, 3)
        testAddDouble("3.142", 3.14159, 3)
        testAddDouble("50.00", 50.00009999999, 2)
        testAddDouble("3.00", 2.997055608097254, 2)
        testAddDouble("-46.00", -45.99696251297749, 2)
    }


    @Test
    fun addDoubleRandom() {
        val rand = Random(System.nanoTime())
        val format = DecimalFormat("0.00")

        for (i in 1 .. 1_000) {
            val value = rand.nextDouble(-100.0, 100.0)
            testAddDouble(format.format(value), value, 2)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun testAddLong(expected: String, value: Long) {
        val record = FlatFileRecord()
        record.add(value)
        assertEquals(expected, record.getString(0))
    }


    private fun testAddDouble(expected: String, value: Double, decimalPlaces: Int) {
        val record = FlatFileRecord()
        record.add(value, decimalPlaces)
        assertEquals(expected, record.getString(0), "value: $value")
    }
}