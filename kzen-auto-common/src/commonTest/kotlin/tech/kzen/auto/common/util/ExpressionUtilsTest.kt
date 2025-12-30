package tech.kzen.auto.common.util

import tech.kzen.auto.common.util.data.FilePath
import tech.kzen.auto.common.util.data.FilePathType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ExpressionUtilsTest {
    @Test
    fun escapeGreaterThan() {
        val escaped = ExpressionUtils.escapeKotlinVariableName("foo -> bar")
        assertEquals("`foo -_ bar`", escaped)
    }
}