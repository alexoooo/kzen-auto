package tech.kzen.auto.common.util

import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.lib.common.model.document.DocumentName
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class PathPatternSubstitutionTest {
    @Test
    fun substitutesExactValuesWithoutRewritingInsertedText() {
        assertEquals(
            "out/a$/nested/__name.csv",
            PathPatternSubstitution.substitute(
                "out/${'$'}{date}.csv",
                mapOf("date" to "a$/nested/__name")))
    }


    @Test
    fun escapedDollarAndReferencedNamesUseTheSameGrammar() {
        val pattern = "root/${'$'}${'$'}/${ '$' }{first}/${ '$' }{second}__x"
        assertEquals(setOf("first", "second"), PathPatternSubstitution.referencedNames(pattern))
        assertEquals(
            "root/$/a/b__x",
            PathPatternSubstitution.substitute(pattern, mapOf("first" to "a", "second" to "b")))
    }


    @Test
    fun malformedAndUnknownVariablesFailClearly() {
        assertFailsWith<IllegalArgumentException> {
            PathPatternSubstitution.substitute("${'$'}{missing}", emptyMap())
        }
        assertFailsWith<IllegalStateException> {
            PathPatternSubstitution.referencedNames("${'$'}{open")
        }
        assertFailsWith<IllegalStateException> {
            PathPatternSubstitution.referencedNames("trailing${'$'}")
        }
        assertFailsWith<IllegalArgumentException> {
            PathPatternSubstitution.substitute("${'$'}bare", emptyMap())
        }
        assertFailsWith<IllegalStateException> {
            PathPatternSubstitution.referencedNames("${'$'}{}")
        }
        assertFailsWith<IllegalStateException> {
            PathPatternSubstitution.referencedNames("${'$'}{outer${'$'}{inner}}")
        }
    }


    @Test
    fun emptyPatternIsAValidLiteral() {
        assertEquals(emptySet(), PathPatternSubstitution.referencedNames(""))
        assertEquals("", PathPatternSubstitution.substitute("", emptyMap()))
    }


    @Test
    fun reportReservedValuesWinAndReportKeepsItsFinalUnderscoreCollapse() {
        val spec = OutputExportSpec(
            "csv", OutputExportSpec.compressionNoneName,
            "${'$'}{custom}__${'$'}{report}.${'$'}{extension}")
        assertEquals(
            "a_b_My_Report.csv",
            spec.resolvePath(
                DocumentName("My Report"), DataLocationGroup.empty,
                Instant.parse("2026-08-24T12:00:00Z"),
                mapOf("custom" to "a__b", "report" to "must-not-win")))
    }
}
