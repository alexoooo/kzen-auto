package tech.kzen.auto.client.objects.document.common.file.format

import tech.kzen.auto.common.data.format.DelimitedFormatOverrideConventions
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class DelimitedFormatOverrideDraftTest {
    @Test
    fun resolvedConfigSeedsAllSupportedControls() {
        val draft = DelimitedFormatOverrideDraft.of(config())

        assertEquals(";", draft.delimiter)
        assertTrue(draft.firstRowHeader)
        assertEquals("windows-1252", draft.encoding)
        assertEquals("2", draft.skipLeadingLines)
        assertEquals("#", draft.commentPrefix)
        assertNull(draft.error)
    }

    @Test
    fun applyUsesCanonicalHeaderAndExplicitClear() {
        val overrides = DelimitedFormatOverrideDraft(
            "\t", false, "UTF-8", "03", "").overrides()

        assertEquals("\t", overrides[DelimitedFormatOverrideConventions.delimiter])
        assertEquals(
            DelimitedFormatOverrideDraft.headerInferLabels,
            overrides[DelimitedFormatOverrideConventions.header])
        assertEquals("UTF-8", overrides[DelimitedFormatOverrideConventions.encoding])
        assertEquals("3", overrides[DelimitedFormatOverrideConventions.skipLeadingLines])
        assertTrue(DelimitedFormatOverrideConventions.commentPrefix in overrides)
        assertNull(overrides[DelimitedFormatOverrideConventions.commentPrefix])
    }

    @Test
    fun delimiterAndSkippedLineValidationAreExact() {
        assertEquals(
            "Delimiter must be exactly one character.",
            DelimitedFormatOverrideDraft("", true, "UTF-8", "0", "").error)
        assertEquals(
            "Delimiter must be exactly one character.",
            DelimitedFormatOverrideDraft("||", true, "UTF-8", "0", "").error)
        assertEquals(
            "Lines to skip must be a nonnegative whole number.",
            DelimitedFormatOverrideDraft(",", true, "UTF-8", "-1", "").error)
    }

    @Test
    fun headerPreviewExplainsSkippedLinesAndDataOutcome() {
        assertEquals(
            "After skipping 2 leading lines, the next row supplies the column names.",
            DelimitedFormatOverrideDraft(",", true, "UTF-8", "2", "").headerExplanation())
        assertEquals(
            "the next row remains data and columns use positional names.",
            DelimitedFormatOverrideDraft(",", false, "UTF-8", "0", "").headerExplanation())
    }

    private fun config(): MapExecutionValue = MapExecutionValue(mapOf(
        "dialect" to MapExecutionValue(mapOf("delimiter" to TextExecutionValue(";"))),
        "header" to MapExecutionValue(mapOf("policy" to TextExecutionValue("present"))),
        "characters" to MapExecutionValue(mapOf("charset" to TextExecutionValue("windows-1252"))),
        "skipLeadingLines" to LongExecutionValue(2),
        "commentPrefix" to TextExecutionValue("#")))
}
