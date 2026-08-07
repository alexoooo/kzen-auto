package tech.kzen.auto.common.util

import tech.kzen.auto.common.util.KotlinExpressionAnalyzer.TokenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


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
    fun rangeOperandsAreReferencedNotTreatedAsMemberSelectors() {
        // `..` is the range operator, so `Count` is a real reference — NOT the `.Count` of a `1.` member
        // access. This is the canonical ForEachStep `items` shape, so misreading it would cost the loop its
        // dependency edge and leave it un-rewritten when `Count` is renamed.
        assertEquals(
            setOf("Count"),
            KotlinExpressionAnalyzer.referencedIdentifiers("1..Count"))

        assertEquals(
            setOf("first", "last"),
            KotlinExpressionAnalyzer.referencedIdentifiers("first..last"))

        // the open-ended form too
        assertEquals(
            setOf("Count"),
            KotlinExpressionAnalyzer.referencedIdentifiers("0..<Count"))

        // and a back-ticked operand
        assertEquals(
            setOf("Outer Item"),
            KotlinExpressionAnalyzer.referencedIdentifiers("1..`Outer Item`"))
    }


    @Test
    fun aMemberSelectorAfterARangeIsStillExcluded() {
        // the `size` of `xs.size` remains a selector even though a range precedes it
        assertEquals(
            setOf("start", "xs"),
            KotlinExpressionAnalyzer.referencedIdentifiers("start..xs.size"))
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


    //-----------------------------------------------------------------------------------------------------------------
    // Every expression the cases above exercise, plus the empty one and a few half-typed ones — the editor
    // re-lexes on every keystroke, so unterminated literals are ordinary input. The structural properties of the
    // token stream hold for any input, so they are asserted over the whole set rather than case by case.
    private val codeFixtures = listOf(
        "",
        "a.",
        "\"abc",
        "\"\${x",
        "x /* unclosed",
        "foo + bar",
        "foo.bar",
        "1..Count",
        "first..last",
        "0..<Count",
        "1..`Outer Item`",
        "start..xs.size",
        "a?.x + b::y",
        "\"foo bar\"",
        "x // foo bar",
        "x /* foo /* nested */ bar */",
        "`my step` + 1",
        "\"\$foo \${bar.baz}\"",
        "\"\"\"plain \$foo end\"\"\"",
        "if (true) x else null",
        "0xFF + 1.5e3",
        "Source + 1",
        "Source + \"Source\" // Source",
        "`My Source` + 2",
        "obj.Source + Source",

        // Nd-category digits that are NOT ASCII: Arabic-Indic, Devanagari, fullwidth. Char.isDigit() accepts all
        // three, so before these were pinned the scanner entered its number branch on a character it then could
        // not consume, and looped forever.
        "١",
        "1١",
        "a१ + 1",
        "１２ + Count")


    private fun tokenText(code: String): List<Pair<TokenKind, String>> {
        return KotlinExpressionAnalyzer.tokens(code).map { it.kind to code.substring(it.start, it.endExclusive) }
    }


    @Test
    fun tokensCoverTheWholeExpressionContiguously() {
        // the highlighting backdrop paints one span per token and concatenates them, so a gap, an overlap or an
        // empty span would corrupt the rendered text
        for (code in codeFixtures) {
            var cursor = 0
            for (token in KotlinExpressionAnalyzer.tokens(code)) {
                assertEquals(cursor, token.start, "gap or overlap in: $code")
                assertTrue(token.endExclusive > token.start, "empty token in: $code")
                cursor = token.endExclusive
            }
            assertEquals(code.length, cursor, "uncovered tail in: $code")
        }
    }


    @Test
    fun identifierReferencesAreExactlyTheIdentifierTokens() {
        for (code in codeFixtures) {
            assertEquals(
                KotlinExpressionAnalyzer.tokens(code)
                    .filter { it.kind == TokenKind.Identifier }
                    .map { it.start to it.endExclusive },
                KotlinExpressionAnalyzer.identifierReferences(code)
                    .map { it.start to it.endExclusive },
                code)
        }
    }


    @Test
    fun nonAsciiDigitIsNotANumber() {
        // Char.isDigit() is Unicode-aware and would call `١` (U+0661) a digit, but the scanner's identifier model
        // is ASCII — so it consumed nothing and never advanced. It is an unrecognized character, not a number.
        assertEquals(
            listOf(TokenKind.Operator to "١"),
            tokenText("١"))

        // an ASCII number stops AT the non-ASCII digit rather than swallowing or stalling on it
        assertEquals(
            listOf(
                TokenKind.Number to "1",
                TokenKind.Operator to "١"),
            tokenText("1١"))

        // likewise it terminates an identifier instead of extending it
        assertEquals(
            listOf(
                TokenKind.Identifier to "a",
                TokenKind.Operator to "१"),
            tokenText("a१"))
    }


    @Test
    fun nestedBlockCommentIsOneToken() {
        assertEquals(
            listOf(
                TokenKind.Identifier to "x",
                TokenKind.Whitespace to " ",
                TokenKind.Comment to "/* foo /* nested */ bar */"),
            tokenText("x /* foo /* nested */ bar */"))
    }


    @Test
    fun rangeOperandIsAnIdentifierRatherThanAMember() {
        assertEquals(
            listOf(
                TokenKind.Number to "1",
                TokenKind.Operator to "..",
                TokenKind.Identifier to "Count"),
            tokenText("1..Count"))
    }


    @Test
    fun safeCallAndCallableReferenceSelectMembers() {
        assertEquals(
            listOf(
                TokenKind.Identifier to "a",
                TokenKind.Operator to "?",
                TokenKind.Operator to ".",
                TokenKind.Member to "x",
                TokenKind.Whitespace to " ",
                TokenKind.Operator to "+",
                TokenKind.Whitespace to " ",
                TokenKind.Identifier to "b",
                TokenKind.Operator to "::",
                TokenKind.Member to "y"),
            tokenText("a?.x + b::y"))
    }


    @Test
    fun backtickedIdentifierTokenSpansItsBackticks() {
        assertEquals(
            listOf(
                TokenKind.Identifier to "`my step`",
                TokenKind.Whitespace to " ",
                TokenKind.Operator to "+",
                TokenKind.Whitespace to " ",
                TokenKind.Number to "1"),
            tokenText("`my step` + 1"))
    }


    @Test
    fun hardKeywordIsItsOwnKind() {
        assertEquals(
            listOf(
                TokenKind.Keyword to "if",
                TokenKind.Whitespace to " ",
                TokenKind.Operator to "(",
                TokenKind.Keyword to "true",
                TokenKind.Operator to ")",
                TokenKind.Whitespace to " ",
                TokenKind.Identifier to "x",
                TokenKind.Whitespace to " ",
                TokenKind.Keyword to "else",
                TokenKind.Whitespace to " ",
                TokenKind.Keyword to "null"),
            tokenText("if (true) x else null"))
    }


    @Test
    fun numericLiteralAbsorbsItsHexAndExponentLetters() {
        assertEquals(
            listOf(
                TokenKind.Number to "0xFF",
                TokenKind.Whitespace to " ",
                TokenKind.Operator to "+",
                TokenKind.Whitespace to " ",
                TokenKind.Number to "1.5e3"),
            tokenText("0xFF + 1.5e3"))
    }


    @Test
    fun templateChunksTheLiteralAroundItsInterpolatedCode() {
        assertEquals(
            listOf(
                TokenKind.StringLiteral to "\"\$",
                TokenKind.Identifier to "foo",
                TokenKind.StringLiteral to " \${",
                TokenKind.Identifier to "bar",
                TokenKind.Operator to ".",
                TokenKind.Member to "baz",
                TokenKind.StringLiteral to "}\""),
            tokenText("\"\$foo \${bar.baz}\""))
    }


    @Test
    fun rawStringTemplateChunksTheLiteralToo() {
        assertEquals(
            listOf(
                TokenKind.StringLiteral to "\"\"\"plain \$",
                TokenKind.Identifier to "foo",
                TokenKind.StringLiteral to " end\"\"\""),
            tokenText("\"\"\"plain \$foo end\"\"\""))
    }
}
