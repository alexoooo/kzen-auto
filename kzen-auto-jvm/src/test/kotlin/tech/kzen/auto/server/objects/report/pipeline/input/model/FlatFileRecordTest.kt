package tech.kzen.auto.server.objects.report.pipeline.input.model

import org.junit.Test
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


    private fun testAddLong(expected: String, value: Long) {
        val record = FlatFileRecord()
        record.add(value)
        assertEquals(expected, record.getString(0))
    }
}