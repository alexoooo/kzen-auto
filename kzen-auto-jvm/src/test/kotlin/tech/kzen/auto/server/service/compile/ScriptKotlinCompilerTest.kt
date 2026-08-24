package tech.kzen.auto.server.service.compile

import tech.kzen.auto.server.objects.script.step.eval.StepExpressionCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Pins the arithmetic that turns a diagnostic's (line, col) into an offset in the USER's expression: the
 * generated wrapper's [KotlinCode.UserCodeRegion] is subtracted, a position outside it is dropped rather than
 * clamped, and the first attributable diagnostic is the one reported.
 *
 * Drives the real Kotlin scripting compiler over real generated source, because every input to that mapping —
 * 1-based line and column, an unpopulated absolute position, a parse error landing one past end of line — is a
 * property of the compiler's reporting rather than of this module.
 */
class ScriptKotlinCompilerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainClassName = "OffsetProbe"

    private val workUtils = WorkUtils.temporary("script-kotlin-compiler-test")


    @AfterTest
    fun tearDown() {
        WorkUtils.recursivelyDeleteDir(workUtils.base())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun validExpressionCompiles() {
        assertIs<KotlinCompilerSuccess>(compile("1 + 1"))
    }


    @Test
    fun forcedReturnRecursivelyImportsDeclaredGenericTypes() {
        val dataUnit = TypeMetadata.of(ClassName("tech.kzen.auto.common.data.model.DataUnit"))
        val list = TypeMetadata(ClassName("kotlin.collections.List"), listOf(dataUnit), false)
        val declared = TypeMetadata(
            ClassName("kotlin.collections.Map"),
            listOf(TypeMetadata.string, list),
            false)
        val generated = StepExpressionCompiler.generateCode(
            mainClassName, declared, "emptyMap()", emptyMap())

        assertTrue(generated.sourceText.contains("import kotlin.collections.Map"))
        assertTrue(generated.sourceText.contains("import kotlin.collections.List"))
        assertTrue(generated.sourceText.contains("import tech.kzen.auto.common.data.model.DataUnit"))
        assertIs<KotlinCompilerSuccess>(
            ScriptKotlinCompiler().compile(
                generated,
                workUtils.base().resolve("$mainClassName-forced.jar"),
                listOf(),
                ClassLoaderUtils.dynamicParentClassLoader()))
    }


    @Test
    fun parseErrorPointsPastTheOffendingText() {
        val code = "1.. 5x"

        // A parse error is reported one past the end of its line, which for a single-line expression is the
        // offset just past its last character — hence the inclusive top of the in-region test.
        assertEquals(code.length, compileError(code).userCodeOffset)
    }


    @Test
    fun unresolvedReferencePointsAtTheIdentifier() {
        val error = compileError("noSuchThing + 1")

        assertEquals(0, error.userCodeOffset)

        // The compiler also reports an uninferrable type parameter on the generated lambda, positioned in the
        // wrapper and emitted BEFORE this one: an unattributable position is skipped, not clamped onto the
        // user's first character.
        assertTrue(
            error.error.contains("Unresolved reference"),
            "expected the user-attributable diagnostic, got: ${error.error}")
    }


    @Test
    fun offsetSpansLinesOfAMultiLineExpression() {
        val code = "val x = 1\nnoSuchThing + x"

        assertEquals(code.indexOf("noSuchThing"), compileError(code).userCodeOffset)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun compile(code: String): KotlinCompilerResult {
        return ScriptKotlinCompiler().compile(
            StepExpressionCompiler.generateInferenceCode(mainClassName, code, mapOf()),
            workUtils.base().resolve("$mainClassName.jar"),
            listOf(),
            ClassLoaderUtils.dynamicParentClassLoader())
    }


    private fun compileError(code: String): KotlinCompilerError {
        return assertIs<KotlinCompilerError>(compile(code))
    }
}
