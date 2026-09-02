package tech.kzen.auto.server.objects.job.value

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.ScalarKind
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals


class JobDataValuesTest {
    @Test
    fun decimalBoundariesRemainExact() {
        val text = "12345678901234567890.1234567890123456789"
        val value = JobDataValues.lift(
            text,
            DataContract(DataType.Scalar(ScalarKind.Decimal)))

        assertEquals(BigDecimal(text), JobDataValues.native(value))
        assertEquals(BigDecimal(text), JobDataValues.boundary(value))
    }
}
