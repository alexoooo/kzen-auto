package tech.kzen.auto.common.util

import kotlin.test.Test
import kotlin.test.assertEquals


class ExpressionUtilsTest {
    @Test
    fun escapeGreaterThan() {
        val escaped = ExpressionUtils.escapeKotlinVariableName("foo -> bar")
        assertEquals("`foo -_ bar`", escaped)
    }
}