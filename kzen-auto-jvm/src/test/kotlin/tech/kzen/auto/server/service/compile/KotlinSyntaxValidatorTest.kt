package tech.kzen.auto.server.service.compile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Unit test for [KotlinSyntaxValidator] — the scope-free parse check. The load-bearing property is the
 * asymmetry: malformed source is rejected regardless of what names it references, while an expression whose
 * names cannot possibly resolve here (CSV columns, a payload receiver's members) still passes, because
 * parsing never resolves anything. A false positive would show a valid Job formula as invalid and block Run.
 */
class KotlinSyntaxValidatorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val validator = KotlinSyntaxValidator()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun stringLiteralFollowedByIdentifierIsRejected() {
        val error = validator.validate("c0 + \"_foo\"ab")
        assertNotNull(error, "two expressions juxtaposed on one line is a syntax error")
    }


    @Test
    fun unbalancedDelimitersAreRejected() {
        assertNotNull(validator.validate("(c0 + c1"), "unclosed parenthesis")
        assertNotNull(validator.validate("c0 + c1)"), "unmatched closing parenthesis")
        assertNotNull(validator.validate("\"unterminated"), "unterminated string literal")
    }


    @Test
    fun danglingOperatorIsRejected() {
        assertNotNull(validator.validate("c0 +"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unresolvableNamesStillParseClean() {
        // The whole point: none of these names exist in any scope the parser knows, and all must pass.
        assertNull(validator.validate("amount.number > 2"))
        assertNull(validator.validate("c0 + \"_foo\""))
        assertNull(validator.validate("nosuchcolumn + 1"))
        assertNull(validator.validate("payload.someMember"))
    }


    @Test
    fun realisticExpressionsParseClean() {
        assertNull(validator.validate("if (c0 == \"x\") 1 else 0"))
        assertNull(validator.validate("\"\${c0}_\${c1}\""), "string template")
        assertNull(validator.validate("listOf(1, 2).map { it * 2 }"), "lambda")
        assertNull(validator.validate("val a = c0.number\na * 2"), "multi-line body with a local")
        assertNull(validator.validate("// just a comment\nc0"), "comment")
    }


    @Test
    fun blankExpressionParsesClean() {
        assertNull(validator.validate(""))
        assertNull(validator.validate("   \n  "))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun errorRendersAgainstTheExpressionsOwnText() {
        // The description's wording belongs to the Kotlin parser and shifts between versions, so only the
        // rendering contract is pinned: description, the offending line of the USER's expression (never the
        // probe scaffolding it was parsed inside), and a caret under it.
        val expression = "c0 + \"_foo\"ab"
        val error = validator.validate(expression)
        assertNotNull(error)

        val lines = error.lines()
        assertEquals(3, lines.size, "expected description / line / caret, got: $error")
        assertTrue(lines[0].isNotBlank(), "description: $error")
        assertEquals(expression, lines[1], "the offending line is the expression itself")
        assertEquals("^", lines[2].trimStart(' '), "caret line: '${lines[2]}'")
        // A caret one past the last character is the "expected something here" position.
        assertTrue(lines[2].length <= expression.length + 1, "caret points beyond the expression: $error")
    }


    @Test
    fun multiLineExpressionRendersOneOfItsOwnLines() {
        // Which line the parser blames is its business (an unclosed construct is reported at its END, not at
        // the opening token); what this pins is that the offset maps back onto the expression's own lines.
        val expression = "val a = c0\nval b = (a\na + b"
        val error = validator.validate(expression)
        assertNotNull(error)

        val lines = error.lines()
        assertTrue(lines[1] in expression.lines(), "reported line '${lines[1]}' is not a line of the expression")
        assertTrue(lines[2].length <= lines[1].length + 1, "caret points beyond its line: $error")
    }
}
