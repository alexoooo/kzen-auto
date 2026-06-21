package tech.kzen.auto.common.util

import kotlin.test.Test
import kotlin.test.assertEquals


class ExpressionUtilsTest {
    @Test
    fun escapeGreaterThan() {
        val escaped = ExpressionUtils.escapeKotlinVariableName("foo -> bar")
        assertEquals("`foo -_ bar`", escaped)
    }


    @Test
    fun singleCharNameStaysPlain() {
        assertEquals("x", ExpressionUtils.escapeKotlinVariableName("x"))
    }


    @Test
    fun leadingUnderscoreNameStaysPlain() {
        assertEquals("_foo", ExpressionUtils.escapeKotlinVariableName("_foo"))
    }


    @Test
    fun spaceNameBackticked() {
        assertEquals("`my step`", ExpressionUtils.escapeKotlinVariableName("my step"))
    }


    @Test
    fun reservedWordBackticked() {
        assertEquals("`class`", ExpressionUtils.escapeKotlinVariableName("class"))
    }


    @Test
    fun identifierContentStripsBackticks() {
        assertEquals("my step", ExpressionUtils.identifierContent("`my step`"))
        assertEquals("foo", ExpressionUtils.identifierContent("foo"))
    }
}