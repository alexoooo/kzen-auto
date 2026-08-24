package tech.kzen.auto.common.util


/**
 * Literal path-pattern substitution shared by Report exports and Job writers. The grammar is deliberately
 * small: `$$` emits one dollar and `${name}` inserts the exact supplied value. Inserted text is never scanned
 * again or normalized, so path separators, dollars and repeated underscores survive verbatim.
 */
object PathPatternSubstitution {
    fun referencedNames(pattern: String): Set<String> {
        val names = linkedSetOf<String>()
        parse(pattern) { name ->
            names.add(name)
            ""
        }
        return names
    }


    fun substitute(pattern: String, values: Map<String, String>): String {
        return parse(pattern) { name ->
            values[name] ?: throw IllegalArgumentException("Unknown path variable: $name")
        }
    }


    private fun parse(pattern: String, replacement: (String) -> String): String {
        val output = StringBuilder(pattern.length)
        var index = 0
        while (index < pattern.length) {
            val character = pattern[index]
            if (character != '$') {
                output.append(character)
                index += 1
                continue
            }

            check(index + 1 < pattern.length) {
                "Malformed path pattern: trailing '$'"
            }
            when (pattern[index + 1]) {
                '$' -> {
                    output.append('$')
                    index += 2
                }

                '{' -> {
                    val close = pattern.indexOf('}', index + 2)
                    check(close != -1) {
                        "Malformed path pattern: missing '}'"
                    }
                    val name = pattern.substring(index + 2, close)
                    check(name.isNotEmpty()) {
                        "Malformed path pattern: empty variable name"
                    }
                    check('$' !in name && '{' !in name) {
                        "Malformed path variable: $name"
                    }
                    output.append(replacement(name))
                    index = close + 1
                }

                else ->
                    throw IllegalArgumentException(
                        "Malformed path pattern at index $index: '$' must be escaped as '$$'")
            }
        }
        return output.toString()
    }
}
