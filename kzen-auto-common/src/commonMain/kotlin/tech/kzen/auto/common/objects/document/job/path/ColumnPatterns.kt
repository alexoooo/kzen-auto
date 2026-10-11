package tech.kzen.auto.common.objects.document.job.path


/**
 * Which columns a writer's `*` keeps, by name: Report's Analysis rule ([allow] is empty or one of its regexes
 * fully matches, and none of [exclude] does). A pattern that doesn't compile matches nothing, and is reported by
 * [errors]. A checked or unchecked column is an [exact] line in one of the lists, so the patterns are the whole
 * selection.
 */
class ColumnPatterns(
    val allow: List<String>,
    val exclude: List<String>
) {
    companion object {
        val none = ColumnPatterns(listOf(), listOf())

        private const val metacharacters = "\\^$.|?*+()[]{}"


        /** The pattern that matches [name] and nothing else, as plain as the name allows (`c0` stays `c0`). */
        fun exact(name: String): String = buildString {
            for (c in name) {
                if (c in metacharacters) {
                    append('\\')
                }
                append(c)
            }
        }


        // Throwable, not Exception: on JS a bad pattern is the native SyntaxError, which is no Kotlin Exception
        private fun compile(patterns: List<String>): List<Regex?> =
            patterns.map {
                try {
                    Regex(it)
                }
                catch (_: Throwable) {
                    null
                }
            }
    }


    private val allowRegexes = compile(allow)
    private val excludeRegexes = compile(exclude)


    fun isEmpty(): Boolean =
        allow.isEmpty() && exclude.isEmpty()


    fun included(name: String): Boolean =
        (allow.isEmpty() || allowRegexes.any { it?.matches(name) == true }) &&
            excludeRegexes.none { it?.matches(name) == true }


    /** An exclude pattern other than [name]'s own [exact] line that excludes it: what a checkbox can't undo. */
    fun excludedBy(name: String): String? {
        val own = exact(name)
        return exclude.withIndex().firstOrNull { (index, pattern) ->
            pattern != own && excludeRegexes[index]?.matches(name) == true
        }?.value
    }


    /** "Allow 2: …" for each pattern that doesn't compile. */
    fun errors(): List<String> =
        errorsOf("Allow", allow) + errorsOf("Exclude", exclude)


    /**
     * The patterns that [include] (or leave out) [name], or null when a broader exclude keeps it out (see
     * [excludedBy]). Leaving out prefers dropping the name's own allow line and otherwise adds an exclude line;
     * including drops the name's own exclude line and, when the allow list doesn't reach it, adds an allow line.
     */
    fun toggled(name: String, include: Boolean): ColumnPatterns? {
        if (included(name) == include) {
            return this
        }
        val own = exact(name)
        if (!include) {
            if (own in allow && allow.size > 1) {
                val withoutAllow = ColumnPatterns(allow - own, exclude)
                if (!withoutAllow.included(name)) {
                    return withoutAllow
                }
            }
            return ColumnPatterns(allow, exclude + own)
        }

        val withoutExclude = ColumnPatterns(allow, exclude.filter { it != own })
        if (withoutExclude.included(name)) {
            return withoutExclude
        }
        if (withoutExclude.excludedBy(name) != null) {
            return null
        }
        return ColumnPatterns(withoutExclude.allow + own, withoutExclude.exclude)
    }


    private fun errorsOf(label: String, patterns: List<String>): List<String> =
        patterns.withIndex().mapNotNull { (index, pattern) ->
            try {
                Regex(pattern)
                null
            }
            catch (e: Throwable) {
                "$label ${index + 1}: ${e.message ?: "invalid pattern"}"
            }
        }


    override fun equals(other: Any?): Boolean =
        other is ColumnPatterns && allow == other.allow && exclude == other.exclude


    override fun hashCode(): Int =
        31 * allow.hashCode() + exclude.hashCode()


    override fun toString(): String =
        "ColumnPatterns(allow=$allow, exclude=$exclude)"
}
