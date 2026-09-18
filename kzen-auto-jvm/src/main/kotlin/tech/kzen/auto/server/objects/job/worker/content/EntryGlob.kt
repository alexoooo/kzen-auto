package tech.kzen.auto.server.objects.job.worker.content


/**
 * The `entries:` header selection (design §7): a list of globs over the entry name, matched in full, where
 * `*` and `?` stay within one path segment and `**` crosses segments. An empty list selects every entry.
 */
internal class EntryGlob(patterns: List<String>) {
    private val regexes: List<Regex> = patterns
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { toRegex(it) }


    fun matches(name: String): Boolean =
        regexes.isEmpty() || regexes.any { it.matches(name) }


    private fun toRegex(glob: String): Regex {
        val out = StringBuilder()
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when {
                c == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> {
                    out.append(".*")
                    i += 1
                }
                c == '*' -> out.append("[^/]*")
                c == '?' -> out.append("[^/]")
                else -> out.append(Regex.escape(c.toString()))
            }
            i += 1
        }
        return Regex(out.toString())
    }
}
