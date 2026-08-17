package tech.kzen.auto.server.objects.report.exec.calc

import org.junit.Test
import kotlin.test.assertEquals


class ColumnValueTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun numberPlusTextConcatenatesNumberText() {
        val sum = ColumnValue.ofNumber(5.0) + ColumnValue.ofText("abc")
        assertEquals("5abc", sum.text)
    }


    @Test
    fun textPlusNumberConcatenatesNumberText() {
        val sum = ColumnValue.ofText("abc") + ColumnValue.ofNumber(5.0)
        assertEquals("abc5", sum.text)
    }


    @Test
    fun numberPlusStringConcatenatesNumberText() {
        val sum = ColumnValue.ofNumber(5.0) + "abc"
        assertEquals("5abc", sum.text)
    }


    @Test
    fun textPlusTextConcatenates() {
        val sum = ColumnValue.ofText("foo") + ColumnValue.ofText("bar")
        assertEquals("foobar", sum.text)
    }


    @Test
    fun numberPlusNumberAdds() {
        val sum = ColumnValue.ofNumber(2.0) + ColumnValue.ofNumber(3.0)
        assertEquals("5", sum.text)
    }
}
