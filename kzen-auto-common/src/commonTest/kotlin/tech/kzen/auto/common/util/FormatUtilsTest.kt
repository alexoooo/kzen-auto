package tech.kzen.auto.common.util

import kotlin.test.Test
import kotlin.test.assertEquals


class FormatUtilsTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun decimalSeparatorLeavesShortNumbersAlone() {
        assertEquals("0", FormatUtils.decimalSeparator(0))
        assertEquals("1", FormatUtils.decimalSeparator(1))
        assertEquals("42", FormatUtils.decimalSeparator(42))
        assertEquals("999", FormatUtils.decimalSeparator(999))
    }


    @Test
    fun decimalSeparatorGroupsFromTheThousandsThreshold() {
        // The threshold where the format changes: 999 is untouched, 1000 gains its first separator.
        assertEquals("1,000", FormatUtils.decimalSeparator(1000))
        assertEquals("9,999", FormatUtils.decimalSeparator(9999))
        assertEquals("10,000", FormatUtils.decimalSeparator(10000))
        assertEquals("999,999", FormatUtils.decimalSeparator(999999))
        assertEquals("1,000,000", FormatUtils.decimalSeparator(1000000))
    }


    @Test
    fun decimalSeparatorHandlesLargeAndNegativeValues() {
        assertEquals("1,234,567,890", FormatUtils.decimalSeparator(1234567890))
        assertEquals("9,223,372,036,854,775,807", FormatUtils.decimalSeparator(Long.MAX_VALUE))

        // The sign is not a digit, so the leading group must still be grouped from the first digit.
        assertEquals("-1,234", FormatUtils.decimalSeparator(-1234))
        assertEquals("-9,223,372,036,854,775,808", FormatUtils.decimalSeparator(Long.MIN_VALUE))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun abbreviateValueLabelsBlankInput() {
        assertEquals("(blank)", FormatUtils.abbreviateValue(""))
        assertEquals("(blank)", FormatUtils.abbreviateValue(" "))
        assertEquals("(blank)", FormatUtils.abbreviateValue("   \t "))
    }


    @Test
    fun abbreviateValuePassesUnderLimitThrough() {
        assertEquals("abc", FormatUtils.abbreviateValue("abc"))

        val underLimit = "x".repeat(95)
        assertEquals(underLimit, FormatUtils.abbreviateValue(underLimit))
    }


    @Test
    fun abbreviateValueTruncatesAtTheLimit() {
        // The limit is exclusive: a value OF the maximum length is already abbreviated.
        val atLimit = "x".repeat(96)
        val abbreviatedAtLimit = FormatUtils.abbreviateValue(atLimit)
        assertEquals("x".repeat(95) + "…", abbreviatedAtLimit)
        assertEquals(96, abbreviatedAtLimit.length)

        val overLimit = "y".repeat(500)
        val abbreviatedOverLimit = FormatUtils.abbreviateValue(overLimit)
        assertEquals("y".repeat(95) + "…", abbreviatedOverLimit)
        assertEquals(96, abbreviatedOverLimit.length)
    }


    @Test
    fun abbreviateValueChargesTheSuffixAgainstTheSameWidth() {
        // A longer suffix keeps less of the original, so the total width is the same either way.
        val overLimit = "z".repeat(500)
        val abbreviated = FormatUtils.abbreviateValue(overLimit, "...")

        assertEquals("z".repeat(93) + "...", abbreviated)
        assertEquals(96, abbreviated.length)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun readableFileSizeFormatsByUnit() {
        assertEquals("0", FormatUtils.readableFileSize(0))
        assertEquals("512 B", FormatUtils.readableFileSize(512))
        assertEquals("1 kB", FormatUtils.readableFileSize(1024))
        assertEquals("1.5 kB", FormatUtils.readableFileSize(1536))
        assertEquals("1 MB", FormatUtils.readableFileSize(1024L * 1024))
        assertEquals("1 GB", FormatUtils.readableFileSize(1024L * 1024 * 1024))
    }
}
