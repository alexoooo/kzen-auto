package tech.kzen.auto.common.util

import tech.kzen.auto.common.data.schema.HeaderLabel


object ExpressionUtils {
    // True Kotlin (ASCII) identifier grammar: a single letter/underscore start, then any letter/digit/underscore.
    // NB: the `+` quantifier in the previous form needed >= 2 chars and forbade a leading `_`, so names like
    //  `x` and `_foo` were needlessly back-ticked — and disagreed with the back-tick stripping in identifierContent.
    private val simpleVariablePattern = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
    private val backticksPattern = Regex("""[^\[(,)/;\\]+""")

    private val escapedPattern = Regex("[^a-zA-Z0-9_]+")
    @Suppress("RegExpSimplifiable")
    private val backticksEscaped = Regex("[>]+")


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
        // Back-ticking a keyword is what makes it referenceable at all, so the set that decides it must be the
        // very set the lexer tokenizes by (see [KotlinExpressionAnalyzer.hardKeywords]).
        if (kotlinVariableName in KotlinExpressionAnalyzer.hardKeywords) {
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
        val backticksEscaped = identifier.replace(backticksEscaped, "_")
        return "`$backticksEscaped`"
    }


    /**
     * The bare identifier text of a (possibly back-tick-quoted) Kotlin identifier: the content inside the
     * back-ticks if quoted, else the identifier itself. This is the canonical key for comparing an identifier
     * token found in an expression (see [tech.kzen.auto.common.util.KotlinExpressionAnalyzer]) against a step
     * name mapped through [escapeKotlinVariableName] — `foo` and `` `foo` `` both reduce to `foo`.
     */
    fun identifierContent(identifier: String): String {
        return if (identifier.length >= 2 && identifier.first() == '`' && identifier.last() == '`') {
            identifier.substring(1, identifier.length - 1)
        }
        else {
            identifier
        }
    }
}