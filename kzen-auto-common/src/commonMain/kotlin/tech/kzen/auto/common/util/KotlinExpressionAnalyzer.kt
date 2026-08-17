package tech.kzen.auto.common.util


/**
 * Platform-agnostic lexical analysis of a Kotlin expression, used to find the in-scope identifiers an
 * expression references, to rewrite a single identifier in place, and to paint the expression in the browser.
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
 * There are three consumers, and they share **one** scan so their views can never drift apart:
 * [referencedIdentifiers] (the dependency gutter) and [renameIdentifier] (rename refactoring) build on
 * [identifierReferences], itself the [TokenKind.Identifier] slice of [tokens]; the client's `KotlinCodeArea`
 * consumes [tokens] directly, to colour each span and to decide whether the caret sits at a completable
 * identifier. Adding a fourth view means deriving it from [tokens] too — never writing a second scanner.
 *
 * [tokens] guarantees a **contiguous** stream: ascending, non-empty, covering exactly `0 until code.length`.
 * That is what lets the renderer concatenate the spans and reproduce the input verbatim, so it is a hard
 * contract, asserted by a property test over every fixture rather than left to inspection.
 */
object KotlinExpressionAnalyzer {
    //-----------------------------------------------------------------------------------------------------------------
    data class IdentifierReference(
        val start: Int,
        val endExclusive: Int,
        val content: String
    )


    enum class TokenKind {
        Whitespace,
        Comment,
        StringLiteral,
        CharLiteral,
        Number,
        Keyword,

        // A name resolved against the enclosing scope — the only kind identifierReferences reports.
        Identifier,

        // The selected name of a member access (the `b` of `a.b`, `a?.b`, `a::b`), which names a member of the
        // receiver rather than anything in scope.
        Member,

        Operator
    }


    data class Token(
        val start: Int,
        val endExclusive: Int,
        val kind: TokenKind
    )


    // Hard (non-soft) keywords can never appear as a bare identifier reference — `true` is the boolean literal,
    // not a variable, while `` `true` `` (back-ticked) IS an identifier. A step named like a keyword is therefore
    // only ever referenced back-ticked, which the lexer captures separately. Soft/contextual keywords (it, field,
    // …) remain valid identifiers, matching Kotlin.
    //
    // This is the ONE definition: [ExpressionUtils.escapeKotlinVariableName] back-ticks exactly this set, so a
    // name the lexer reads as a keyword is a name escaping quoted. A second copy would let escaping and
    // tokenization drift, silently dropping the dependency edge and the rename for the divergent names.
    // https://stackoverflow.com/a/44149580/1941359
    val hardKeywords = setOf(
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
        // ascending and non-overlapping by the tokens contract, which renameIdentifier's single pass relies on
        return tokens(code)
            .filter { it.kind == TokenKind.Identifier }
            .map { IdentifierReference(it.start, it.endExclusive, identifierContent(code, it)) }
    }


    /**
     * Every lexical token of [code], in ascending order, gap-free and covering exactly `0 until code.length` —
     * a syntax-highlighting backdrop paints one span per token and concatenates them, so a gap or an overlap
     * would corrupt the text it renders. A string template is chunked rather than emitted whole: the literal
     * runs up to and including the `$` or `${`, the interpolated code contributes its own tokens, then the
     * literal resumes.
     */
    fun tokens(code: String): List<Token> {
        val sink = mutableListOf<Token>()
        scanCode(code, 0, code.length, sink)
        return sink
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A back-ticked identifier's token spans the back-ticks (so a rename replaces them) while its content drops
    // them, reducing `foo` and `` `foo` `` to the same key.
    private fun identifierContent(code: String, token: Token): String {
        return when (code[token.start]) {
            '`' -> code.substring(token.start + 1, token.endExclusive - 1)
            else -> code.substring(token.start, token.endExclusive)
        }
    }


    // Scans code[from until to] in code mode, appending tokens. Re-entered for `${ … }` templates.
    private fun scanCode(code: String, from: Int, to: Int, sink: MutableList<Token>) {
        var i = from
        // true when the previous significant token was `.` or `::`, i.e. the next identifier is a member selector
        var afterMemberSelector = false

        while (i < to) {
            val c = code[i]
            when {
                c.isWhitespace() -> {
                    var j = i + 1
                    while (j < to && code[j].isWhitespace()) {
                        j++
                    }
                    sink.add(Token(i, j, TokenKind.Whitespace))
                    i = j  // whitespace does not break a member-access chain (a . b)
                }

                c == '/' && i + 1 < to && code[i + 1] == '/' -> {
                    val end = skipLineComment(code, i, to)
                    sink.add(Token(i, end, TokenKind.Comment))
                    i = end
                }

                c == '/' && i + 1 < to && code[i + 1] == '*' -> {
                    val end = skipBlockComment(code, i, to)
                    sink.add(Token(i, end, TokenKind.Comment))
                    i = end
                }

                c == '"' -> {
                    i = scanString(code, i, to, sink)
                    afterMemberSelector = false
                }

                c == '\'' -> {
                    val end = skipCharLiteral(code, i, to)
                    sink.add(Token(i, end, TokenKind.CharLiteral))
                    i = end
                    afterMemberSelector = false
                }

                c == '`' -> {
                    val end = backtickEnd(code, i, to)
                    sink.add(Token(i, end, if (afterMemberSelector) TokenKind.Member else TokenKind.Identifier))
                    i = end
                    afterMemberSelector = false
                }

                isIdentifierStart(c) -> {
                    var j = i + 1
                    while (j < to && isIdentifierPart(code[j])) {
                        j++
                    }
                    val kind = when {
                        code.substring(i, j) in hardKeywords -> TokenKind.Keyword
                        afterMemberSelector -> TokenKind.Member
                        else -> TokenKind.Identifier
                    }
                    sink.add(Token(i, j, kind))
                    i = j
                    afterMemberSelector = false
                }

                isDigit(c) -> {
                    val end = skipNumber(code, i, to)
                    sink.add(Token(i, end, TokenKind.Number))
                    i = end
                    afterMemberSelector = false
                }

                // `..` / `..<` is the RANGE operator, not two member selectors, so what follows it is a
                // genuine reference. Load-bearing rather than a nicety: `1..Count` is the canonical
                // ForEachStep `items` expression, and reading it as `1.` then `.Count` would drop the
                // dependency edge and leave the expression silently un-rewritten by a rename of `Count`.
                // The trailing `<` of `..<` falls through to the catch-all below, which is a no-op here.
                c == '.' && i + 1 < to && code[i + 1] == '.' -> {
                    sink.add(Token(i, i + 2, TokenKind.Operator))
                    afterMemberSelector = false
                    i += 2
                }

                c == '.' -> {
                    sink.add(Token(i, i + 1, TokenKind.Operator))
                    afterMemberSelector = true
                    i++
                }

                c == ':' && i + 1 < to && code[i + 1] == ':' -> {
                    sink.add(Token(i, i + 2, TokenKind.Operator))
                    afterMemberSelector = true
                    i += 2
                }

                else -> {
                    sink.add(Token(i, i + 1, TokenKind.Operator))
                    afterMemberSelector = false
                    i++
                }
            }
        }
    }


    // Scans a string literal starting at the opening `"` (single or triple), emitting it as chunks split around
    // its template interpolations, whose contents are scanned as code. Returns the index past the closing
    // quote(s).
    private fun scanString(code: String, start: Int, to: Int, sink: MutableList<Token>): Int {
        val triple = start + 2 < to && code[start + 1] == '"' && code[start + 2] == '"'
        var chunkStart = start
        var i = if (triple) start + 3 else start + 1

        while (i < to) {
            val c = code[i]

            if (triple) {
                if (c == '"' && i + 2 < to && code[i + 1] == '"' && code[i + 2] == '"') {
                    sink.add(Token(chunkStart, i + 3, TokenKind.StringLiteral))
                    return i + 3
                }
            }
            else {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == '"') {
                    sink.add(Token(chunkStart, i + 1, TokenKind.StringLiteral))
                    return i + 1
                }
                if (c == '\n') {
                    sink.add(Token(chunkStart, i + 1, TokenKind.StringLiteral))
                    return i + 1  // unterminated single-line string; bail
                }
            }

            if (c == '$' && i + 1 < to) {
                val next = code[i + 1]
                if (next == '{') {
                    sink.add(Token(chunkStart, i + 2, TokenKind.StringLiteral))
                    val innerEnd = templateBraceEnd(code, i + 2, to)
                    scanCode(code, i + 2, innerEnd, sink)
                    chunkStart = innerEnd  // the closing `}` opens the next literal chunk
                    i = if (innerEnd < to) innerEnd + 1 else to
                    continue
                }
                if (isIdentifierStart(next)) {
                    sink.add(Token(chunkStart, i + 1, TokenKind.StringLiteral))
                    var j = i + 2
                    while (j < to && isIdentifierPart(code[j])) {
                        j++
                    }
                    val content = code.substring(i + 1, j)
                    val kind = if (content in hardKeywords) TokenKind.Keyword else TokenKind.Identifier
                    sink.add(Token(i + 1, j, kind))
                    chunkStart = j
                    i = j
                    continue
                }
            }

            i++
        }

        // the trailing chunk is empty when an unterminated `${` ran its interpolated code to the end
        if (chunkStart < to) {
            sink.add(Token(chunkStart, to, TokenKind.StringLiteral))
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
            else if (c == '.' && i + 1 < to && isDigit(code[i + 1])) {
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
        return isIdentifierStart(c) || isDigit(c)
    }


    // ASCII-only, deliberately NOT Char.isDigit(), which is Unicode-aware and accepts every Nd-category digit
    // (Arabic-Indic `١`, Devanagari `१`, fullwidth `１`, …). Kotlin's own number literals are ASCII, and this
    // lexer's identifier model is ASCII, so a Unicode-aware digit test here would disagree with isIdentifierPart:
    // scanCode would enter the number branch on a character skipNumber then refuses to consume, emitting a
    // zero-width token and never advancing — an infinite loop that hangs the browser tab, since the editor
    // re-lexes on every keystroke. Keeping the one notion of "digit" makes that unrepresentable.
    private fun isDigit(c: Char): Boolean {
        return c in '0'..'9'
    }
}
