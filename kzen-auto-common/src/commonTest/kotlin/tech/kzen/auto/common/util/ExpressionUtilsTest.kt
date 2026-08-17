package tech.kzen.auto.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ExpressionUtilsTest {
    @Test
    fun everyHardKeywordEscapesIntoAReferenceTheLexerReadsBack() {
        // Escaping and tokenization share one keyword set precisely so this holds for all of them: a step
        // named like a keyword is referenced back-ticked, and the lexer reports that back-ticked form as an
        // identifier reference rather than as the keyword.
        for (keyword in KotlinExpressionAnalyzer.hardKeywords) {
            val escaped = ExpressionUtils.escapeKotlinVariableName(keyword)
            assertEquals("`$keyword`", escaped, keyword)
            assertEquals(keyword, ExpressionUtils.identifierContent(escaped), keyword)

            assertTrue(
                keyword in KotlinExpressionAnalyzer.referencedIdentifiers("$escaped + 1"),
                keyword)
            assertTrue(
                keyword !in KotlinExpressionAnalyzer.referencedIdentifiers("$keyword + 1"),
                keyword)
        }
    }



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