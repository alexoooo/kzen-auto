package tech.kzen.auto.server.service.compile

import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtPsiFactory


/**
 * Scope-free syntax check of a user Kotlin expression: parses it and reports the first syntax error, without
 * resolving a single name.
 *
 * This is what makes an expression checkable where its scope is NOT statically known — a Job Worker reading a
 * CSV lane, whose columns exist only once the file is read (see
 * [tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor]). A full compile
 * ([tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval.validate]) cannot run there: with no
 * column accessors generated, every column reference resolves to nothing, so a perfectly good expression would
 * be reported as invalid. Parsing has no scope at all, so it reports only what holds under EVERY possible
 * header. Syntax errors are a strict subset of compile errors — a clean parse does not promise the expression
 * compiles, but a parse error is always a real one.
 */
class KotlinSyntaxValidator {
    //-----------------------------------------------------------------------------------------------------------------
    private companion object {
        private const val probeFileName = "probe.kt"

        // The expression is parsed in a statement block, the position the generated column code also puts it in
        // (CalculatedColumnEval nests it in a lambda's `run { … }`), so a multi-statement expression parses the
        // same way here. A function header rather than a lambda's, so a leading `->` cannot be read as a
        // parameter list and mask an error. The newlines keep the expression's own offsets un-shifted.
        private const val probePrefix = "fun probe() {\n"
        private const val probeSuffix = "\n}"
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return null when [expression] parses cleanly, otherwise the first syntax error rendered against the
     *  expression's own text (description, offending line, caret) — the shape a compile error arrives in, so
     *  both render identically on a Worker's card.
     */
    fun validate(expression: String): String? {
        val probeCode = probePrefix + expression + probeSuffix

        // Parsing is stateless and frontend-independent (both frontends read the same PSI tree and its
        // PsiErrorElements; nothing is resolved), so the one standing project serves every check
        val probeFile = KtPsiFactory(KotlinApplicationEnvironment.project, markGenerated = false)
            .createFile(probeFileName, probeCode)

        val error = PsiTreeUtil.findChildOfType(probeFile, PsiErrorElement::class.java)
            ?: return null

        return render(expression, error.errorDescription, error.textOffset - probePrefix.length)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // An error at an unclosed construct points past the expression (into the probe's own closing brace), so the
    // offset is clamped rather than trusted.
    private fun render(expression: String, errorDescription: String, offset: Int): String {
        val errorOffset = offset.coerceIn(0, expression.length)

        val lineStart =
            if (errorOffset == 0) {
                0
            }
            else {
                expression.lastIndexOf('\n', errorOffset - 1) + 1
            }

        val nextNewline = expression.indexOf('\n', errorOffset)
        val lineEnd = if (nextNewline == -1) { expression.length } else { nextNewline }

        val line = expression.substring(lineStart, lineEnd)
        val caret = " ".repeat(errorOffset - lineStart) + "^"

        return "$errorDescription\n$line\n$caret"
    }
}
