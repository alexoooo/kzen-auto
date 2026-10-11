package tech.kzen.auto.common.objects.document.job.path

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


/** Which payload columns a writer's `*` keeps, and what checking one writes. */
class ColumnPatternsTest {
    @Test
    fun allowKeepsOnlyWhatItMatchesWholeAndExcludeWins() {
        val patterns = ColumnPatterns(listOf("c.*"), listOf("c2"))
        assertTrue(patterns.included("c0"))
        assertFalse(patterns.included("c2"))
        assertFalse(patterns.included("xc0"), "an allow pattern matches the whole name")
        assertTrue(ColumnPatterns.none.included("anything"))
    }


    @Test
    fun anExactLineEscapesOnlyWhatARegexWouldRead() {
        assertEquals("c0", ColumnPatterns.exact("c0"))
        assertEquals("price \\(usd\\)", ColumnPatterns.exact("price (usd)"))
        assertTrue(ColumnPatterns(listOf(ColumnPatterns.exact("a.b")), listOf()).included("a.b"))
        assertFalse(ColumnPatterns(listOf(ColumnPatterns.exact("a.b")), listOf()).included("axb"))
    }


    @Test
    fun uncheckingAddsAnExcludeLineAndCheckingTakesItBack() {
        val unchecked = ColumnPatterns.none.toggled("c0", false)
        assertEquals(ColumnPatterns(listOf(), listOf("c0")), unchecked)
        assertEquals(ColumnPatterns.none, unchecked?.toggled("c0", true))
    }


    @Test
    fun checkingAColumnTheAllowListMissesAddsItThere() {
        val patterns = ColumnPatterns(listOf("c0"), listOf())
        assertEquals(ColumnPatterns(listOf("c0", "c1"), listOf()), patterns.toggled("c1", true))
    }


    @Test
    fun uncheckingTheOnlyAllowedColumnExcludesItRatherThanAllowingEverything() {
        val patterns = ColumnPatterns(listOf("c0"), listOf())
        assertEquals(ColumnPatterns(listOf("c0"), listOf("c0")), patterns.toggled("c0", false))
        assertEquals(
            ColumnPatterns(listOf("c1"), listOf()),
            ColumnPatterns(listOf("c0", "c1"), listOf()).toggled("c0", false))
    }


    @Test
    fun aBroaderExcludeCannotBeUndoneByACheckbox() {
        val patterns = ColumnPatterns(listOf(), listOf("tmp_.*"))
        assertNull(patterns.toggled("tmp_a", true))
        assertEquals("tmp_.*", patterns.excludedBy("tmp_a"))
        assertNull(ColumnPatterns(listOf(), listOf("c0")).excludedBy("c0"), "its own line is not broader")
    }


    @Test
    fun aPatternThatDoesNotCompileMatchesNothingAndSaysWhy() {
        val patterns = ColumnPatterns(listOf("("), listOf())
        assertFalse(patterns.included("("))
        assertEquals(1, patterns.errors().size)
        assertTrue(patterns.errors().single().startsWith("Allow 1: "))
    }
}
