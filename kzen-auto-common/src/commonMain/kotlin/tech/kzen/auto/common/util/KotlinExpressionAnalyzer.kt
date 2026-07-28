package tech.kzen.auto.common.util


/**
 * Platform-agnostic lexical analysis of a Kotlin expression, used to find the in-scope identifiers an
 * expression references and to rewrite a single identifier in place.
 *
 * This is a *lexer*, not a type-resolver: it scans the source respecting Kotlin's lexical grammar — line
 * comments, (nestable) block comments, string literals (normal and raw triple-quoted), char literals,
 * string-template interpolations (`$id` and `${ … }`, whose contents ARE code), and back-tick-quoted
 * identifiers — and excludes member-access selectors (the `b` in `a.b`, `a?.b`, `a::b`). It does NOT resolve
 * semantic shadowing (a local `val foo` masking an in-scope `foo`); that is the one case a regex-free lexer
 * still can't catch, and is accepted as out of scope.
 *
 * Identifier "content" is the bare text with back-ticks stripped, so a step named `foo` referenced as either
 * `foo` or `` `foo` `` reduces to the same key. Map a step name to its content with
 * `ExpressionUtils.identifierContent(ExpressionUtils.escapeKotlinVariableName(name))`.
 *
 * Both [referencedIdentifiers] (the dependency gutter) and [renameIdentifier] (rename refactoring) build on
 * the single [identifierReferences] scan, so they agree by construction.
 */
object KotlinExpressionAnalyzer {
    //-----------------------------------------------------------------------------------------------------------------
    data class IdentifierReference(
        val start: Int,
        val endExclusive: Int,
        val content: String
    )


    // Hard (non-soft) keywords can never appear as a bare identifier reference — `true` is the boolean literal,
    // not a variable, while `` `true` `` (back-ticked) IS an identifier. A step named like a keyword is therefore
    // only ever referenced back-ticked, which the lexer captures separately. Soft/contextual keywords (it, field,
    // …) remain valid identifiers, matching Kotlin.
    private val hardKeywords = setOf(
        "package", "as", "typealias", "class", "this", "super", "val", "var", "fun", "for",
        "null", "true", "false", "is", "in", "throw", "return", "break", "continue", "object",
        "if", "try", "else", "while", "do", "when", "interface", "typeof")


    //-----------------------------------------------------------------------------------------------------------------
    fun referencedIdentifiers(code: String): Set<String> {
        return identifierReferences(code).mapTo(mutableSetOf()) { it.content }
    }


    /**
     * Replaces every identifier reference whose content equals [fromContent] with [toEscaped] (the already
     * back-tick-escaped new name, e.g. from `ExpressionUtils.escapeKotlinVariableName`), leaving strings,
     * comments, member selectors and everything else byte-for-byte untouched.
     */
    fun renameIdentifier(code: String, fromContent: String, toEscaped: String): String {
        val matches = identifierReferences(code).filter { it.content == fromContent }
        if (matches.isEmpty()) {
            return code
        }

        val builder = StringBuilder()
        var cursor = 0
        for (match in matches) {
            builder.append(code, cursor, match.start)
            builder.append(toEscaped)
            cursor = match.endExclusive
        }
        builder.append(code, cursor, code.length)
        return builder.toString()
    }


    fun identifierReferences(code: String): List<IdentifierReference> {
        val results = mutableListOf<IdentifierReference>()
        scanCode(code, 0, code.length, results)
        // scan order is already ascending by start; sort defensively so renameIdentifier's single pass is safe
        results.sortBy { it.start }
        return results
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Scans code[from until to] in code mode, appending identifier references. Re-entered for `${ … }` templates.
    private fun scanCode(code: String, from: Int, to: Int, results: MutableList<IdentifierReference>) {
        var i = from
        // true when the previous significant token was `.` or `::`, i.e. the next identifier is a member selector
        var afterMemberSelector = false

        while (i < to) {
            val c = code[i]
            when {
                c.isWhitespace() ->
                    i++  // whitespace does not break a member-access chain (a . b)

                c == '/' && i + 1 < to && code[i + 1] == '/' ->
                    i = skipLineComment(code, i, to)

                c == '/' && i + 1 < to && code[i + 1] == '*' ->
                    i = skipBlockComment(code, i, to)

                c == '"' -> {
                    i = scanString(code, i, to, results)
                    afterMemberSelector = false
                }

                c == '\'' -> {
                    i = skipCharLiteral(code, i, to)
                    afterMemberSelector = false
                }

                c == '`' -> {
                    val end = backtickEnd(code, i, to)
                    if (!afterMemberSelector) {
                        results.add(IdentifierReference(i, end, code.substring(i + 1, end - 1)))
                    }
                    i = end
                    afterMemberSelector = false
                }

                isIdentifierStart(c) -> {
                    var j = i + 1
                    while (j < to && isIdentifierPart(code[j])) {
                        j++
                    }
                    val content = code.substring(i, j)
                    if (!afterMemberSelector && content !in hardKeywords) {
                        results.add(IdentifierReference(i, j, content))
                    }
                    i = j
                    afterMemberSelector = false
                }

                c.isDigit() -> {
                    i = skipNumber(code, i, to)
                    afterMemberSelector = false
                }

                // `..` / `..<` is the RANGE operator, not two member selectors, so what follows it is a
                // genuine reference. Load-bearing rather than a nicety: `1..Count` is the canonical
                // ForEachStep `items` expression, and reading it as `1.` then `.Count` would drop the
                // dependency edge and leave the expression silently un-rewritten by a rename of `Count`.
                // The trailing `<` of `..<` falls through to the catch-all below, which is a no-op here.
                c == '.' && i + 1 < to && code[i + 1] == '.' -> {
                    afterMemberSelector = false
                    i += 2
                }

                c == '.' -> {
                    afterMemberSelector = true
                    i++
                }

                c == ':' && i + 1 < to && code[i + 1] == ':' -> {
                    afterMemberSelector = true
                    i += 2
                }

                else -> {
                    afterMemberSelector = false
                    i++
                }
            }
        }
    }


    // Scans a string literal starting at the opening `"` (single or triple), collecting references from its
    // template interpolations. Returns the index past the closing quote(s).
    private fun scanString(code: String, start: Int, to: Int, results: MutableList<IdentifierReference>): Int {
        val triple = start + 2 < to && code[start + 1] == '"' && code[start + 2] == '"'
        var i = if (triple) start + 3 else start + 1

        while (i < to) {
            val c = code[i]

            if (triple) {
                if (c == '"' && i + 2 < to && code[i + 1] == '"' && code[i + 2] == '"') {
                    return i + 3
                }
            }
            else {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == '"') {
                    return i + 1
                }
                if (c == '\n') {
                    return i + 1  // unterminated single-line string; bail
                }
            }

            if (c == '$' && i + 1 < to) {
                val next = code[i + 1]
                if (next == '{') {
                    val innerEnd = templateBraceEnd(code, i + 2, to)
                    scanCode(code, i + 2, innerEnd, results)
                    i = if (innerEnd < to) innerEnd + 1 else to
                    continue
                }
                if (isIdentifierStart(next)) {
                    var j = i + 2
                    while (j < to && isIdentifierPart(code[j])) {
                        j++
                    }
                    val content = code.substring(i + 1, j)
                    if (content !in hardKeywords) {
                        results.add(IdentifierReference(i + 1, j, content))
                    }
                    i = j
                    continue
                }
            }

            i++
        }

        return to
    }


    // Finds the `}` that closes a `${` template opened just before `from` (depth starts at 1), skipping nested
    // strings/chars/comments/braces so a brace inside a string doesn't close the template. Returns its index.
    private fun templateBraceEnd(code: String, from: Int, to: Int): Int {
        var i = from
        var depth = 1

        while (i < to) {
            val c = code[i]
            when {
                c == '/' && i + 1 < to && code[i + 1] == '/' ->
                    i = skipLineComment(code, i, to)

                c == '/' && i + 1 < to && code[i + 1] == '*' ->
                    i = skipBlockComment(code, i, to)

                c == '"' ->
                    i = skipString(code, i, to)

                c == '\'' ->
                    i = skipCharLiteral(code, i, to)

                c == '`' ->
                    i = backtickEnd(code, i, to)

                c == '{' -> {
                    depth++
                    i++
                }

                c == '}' -> {
                    depth--
                    if (depth == 0) {
                        return i
                    }
                    i++
                }

                else ->
                    i++
            }
        }

        return to
    }


    // Skips a string literal without collecting references (used only to find a template's closing brace).
    private fun skipString(code: String, start: Int, to: Int): Int {
        val triple = start + 2 < to && code[start + 1] == '"' && code[start + 2] == '"'
        var i = if (triple) start + 3 else start + 1

        while (i < to) {
            val c = code[i]

            if (triple) {
                if (c == '"' && i + 2 < to && code[i + 1] == '"' && code[i + 2] == '"') {
                    return i + 3
                }
            }
            else {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == '"') {
                    return i + 1
                }
                if (c == '\n') {
                    return i + 1
                }
            }

            if (c == '$' && i + 1 < to && code[i + 1] == '{') {
                val innerEnd = templateBraceEnd(code, i + 2, to)
                i = if (innerEnd < to) innerEnd + 1 else to
                continue
            }

            i++
        }

        return to
    }


    private fun skipLineComment(code: String, start: Int, to: Int): Int {
        var i = start + 2
        while (i < to && code[i] != '\n') {
            i++
        }
        return i
    }


    private fun skipBlockComment(code: String, start: Int, to: Int): Int {
        var i = start + 2
        var depth = 1
        while (i < to && depth > 0) {
            if (i + 1 < to && code[i] == '/' && code[i + 1] == '*') {
                depth++
                i += 2
            }
            else if (i + 1 < to && code[i] == '*' && code[i + 1] == '/') {
                depth--
                i += 2
            }
            else {
                i++
            }
        }
        return i
    }


    private fun skipCharLiteral(code: String, start: Int, to: Int): Int {
        var i = start + 1
        while (i < to) {
            val c = code[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == '\'' || c == '\n') {
                return i + 1
            }
            i++
        }
        return to
    }


    // `start` is the opening back-tick; returns the index past the closing back-tick (or `to` if unterminated).
    private fun backtickEnd(code: String, start: Int, to: Int): Int {
        var i = start + 1
        while (i < to && code[i] != '`') {
            i++
        }
        return if (i < to) i + 1 else to
    }


    // Consumes a numeric literal so its digits/hex letters/exponent/suffix are never mistaken for an identifier
    // (e.g. the `xFF` of `0xFF`), while stopping before a `.member` selector.
    private fun skipNumber(code: String, start: Int, to: Int): Int {
        var i = start
        while (i < to) {
            val c = code[i]
            if (isIdentifierPart(c)) {
                i++
            }
            else if (c == '.' && i + 1 < to && code[i + 1].isDigit()) {
                i++
            }
            else {
                break
            }
        }
        return i
    }


    private fun isIdentifierStart(c: Char): Boolean {
        return c == '_' || c in 'a'..'z' || c in 'A'..'Z'
    }


    private fun isIdentifierPart(c: Char): Boolean {
        return isIdentifierStart(c) || c in '0'..'9'
    }
}
