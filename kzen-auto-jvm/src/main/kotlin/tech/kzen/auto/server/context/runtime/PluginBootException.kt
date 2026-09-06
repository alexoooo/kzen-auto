package tech.kzen.auto.server.context.runtime


/**
 * A plugin universe that cannot be pinned: duplicate or reserved scope ids, an SPI version mismatch, or a
 * second runtime initialization with a conflicting configuration. Every boot error found is listed at once.
 */
class PluginBootException(
    val errors: List<String>
): IllegalStateException(errors.joinToString("\n"))
