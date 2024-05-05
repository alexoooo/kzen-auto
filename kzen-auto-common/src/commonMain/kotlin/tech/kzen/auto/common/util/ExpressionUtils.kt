package tech.kzen.auto.common.util

import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


object ExpressionUtils {
    // https://stackoverflow.com/a/44149580/1941359
    private val reservedWords = setOf(
        "package", "as", "typealias", "class", "this", "super", "val", "var", "fun", "for",
        "null", "true", "false", "is", "in", "throw", "return", "break", "continue", "object",
        "if", "try", "else", "while", "do", "when", "interface", "typeof")

    private val simpleVariablePattern = Regex("[a-zA-Z][a-zA-Z0-9_]+")
//    private val backticksPattern = Regex("[^\\[(,)/;\\\\]+")
    private val backticksPattern = Regex("""[^\[(,)/;\\]+""")

    private val escapedPattern = Regex("[^a-zA-Z0-9_]+")


    fun escapeKotlinVariableName(headerLabel: HeaderLabel): String {
        // TODO: handle variable escape in the context of the full set of variables,
        //  because escaping can cause it's own 2nd order name collisions

        val variableName = when {
            headerLabel.occurrence == 0 -> headerLabel.text
            else -> "${headerLabel.text}_${headerLabel.occurrence + 1}"
        }
        return escapeKotlinVariableName(variableName)
    }


    fun escapeKotlinVariableName(kotlinVariableName: String): String {
        if (kotlinVariableName in reservedWords) {
            return backticksQuote(kotlinVariableName)
        }

        if (simpleVariablePattern.matches(kotlinVariableName)) {
            return kotlinVariableName
        }

        if (backticksPattern.matches(kotlinVariableName)) {
            return backticksQuote(kotlinVariableName)
        }

        val escaped = kotlinVariableName.replace(escapedPattern, "_")
        if (simpleVariablePattern.matches(escaped)) {
            return escaped
        }

        return backticksQuote(escaped)
    }


    private fun backticksQuote(identifier: String): String {
        return "`$identifier`"
    }
}