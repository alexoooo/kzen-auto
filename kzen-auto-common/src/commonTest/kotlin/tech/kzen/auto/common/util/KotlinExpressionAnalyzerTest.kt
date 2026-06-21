package tech.kzen.auto.common.util

import kotlin.test.Test
import kotlin.test.assertEquals


class KotlinExpressionAnalyzerTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun plainIdentifiers() {
        assertEquals(
            setOf("foo", "bar"),
            KotlinExpressionAnalyzer.referencedIdentifiers("foo + bar"))
    }


    @Test
    fun memberSelectorExcluded() {
        // the `bar` of `foo.bar` is a member selector, not an in-scope reference
        assertEquals(
            setOf("foo"),
            KotlinExpressionAnalyzer.referencedIdentifiers("foo.bar"))
    }


    @Test
    fun safeCallAndCallableReferenceExcluded() {
        assertEquals(
            setOf("a", "b"),
            KotlinExpressionAnalyzer.referencedIdentifiers("a?.x + b::y"))
    }


    @Test
    fun stringLiteralNotReferenced() {
        assertEquals(
            emptySet<String>(),
            KotlinExpressionAnalyzer.referencedIdentifiers("\"foo bar\""))
    }


    @Test
    fun lineCommentNotReferenced() {
        assertEquals(
            setOf("x"),
            KotlinExpressionAnalyzer.referencedIdentifiers("x // foo bar"))
    }


    @Test
    fun nestedBlockCommentNotReferenced() {
        assertEquals(
            setOf("x"),
            KotlinExpressionAnalyzer.referencedIdentifiers("x /* foo /* nested */ bar */"))
    }


    @Test
    fun backtickIdentifierReferenced() {
        assertEquals(
            setOf("my step"),
            KotlinExpressionAnalyzer.referencedIdentifiers("`my step` + 1"))
    }


    @Test
    fun templateInterpolationsReferenced() {
        // "$foo ${bar.baz}" — foo (simple template) and bar (braced template) are references; baz is a member
        assertEquals(
            setOf("foo", "bar"),
            KotlinExpressionAnalyzer.referencedIdentifiers("\"\$foo \${bar.baz}\""))
    }


    @Test
    fun rawStringTemplateReferenced() {
        assertEquals(
            setOf("foo"),
            KotlinExpressionAnalyzer.referencedIdentifiers("\"\"\"plain \$foo end\"\"\""))
    }


    @Test
    fun hardKeywordsNotReferenced() {
        assertEquals(
            setOf("x"),
            KotlinExpressionAnalyzer.referencedIdentifiers("if (true) x else null"))
    }


    @Test
    fun numericLiteralsNotIdentifiers() {
        assertEquals(
            emptySet<String>(),
            KotlinExpressionAnalyzer.referencedIdentifiers("0xFF + 1.5e3"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun renamePlain() {
        assertEquals(
            "Renamed + 1",
            KotlinExpressionAnalyzer.renameIdentifier("Source + 1", "Source", "Renamed"))
    }


    @Test
    fun renameLeavesStringsAndCommentsUntouched() {
        assertEquals(
            "Renamed + \"Source\" // Source",
            KotlinExpressionAnalyzer.renameIdentifier("Source + \"Source\" // Source", "Source", "Renamed"))
    }


    @Test
    fun renameToBacktickedName() {
        assertEquals(
            "`My Target` + 2",
            KotlinExpressionAnalyzer.renameIdentifier("`My Source` + 2", "My Source", "`My Target`"))
    }


    @Test
    fun renameLeavesMemberSelectorUntouched() {
        // only the standalone `Source` is rewritten, not the `.Source` member access
        assertEquals(
            "obj.Source + Renamed",
            KotlinExpressionAnalyzer.renameIdentifier("obj.Source + Source", "Source", "Renamed"))
    }
}
