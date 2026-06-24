package tech.kzen.auto.server.objects.job.worker

import java.nio.file.Path


/**
 * Interpret a user-entered file-path attribute as a [Path]. Trims surrounding whitespace and strips one
 * pair of surrounding quotes — Windows Explorer's "Copy as path" wraps the path in double quotes, and a
 * shell-style paste may use single quotes — so a pasted path works as-is instead of failing [Path.of] on
 * the leading quote character (which is an illegal path char on Windows).
 */
internal fun toFilePath(raw: String): Path {
    val trimmed = raw.trim()

    val unquoted =
        if (trimmed.length >= 2 &&
            (trimmed.first() == '"' && trimmed.last() == '"' ||
                trimmed.first() == '\'' && trimmed.last() == '\'')
        ) {
            trimmed.substring(1, trimmed.length - 1)
        }
        else {
            trimmed
        }

    return Path.of(unquoted)
}
