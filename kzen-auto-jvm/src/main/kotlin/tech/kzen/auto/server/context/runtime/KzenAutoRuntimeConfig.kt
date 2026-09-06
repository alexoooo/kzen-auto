package tech.kzen.auto.server.context.runtime

import java.nio.file.Path
import java.nio.file.Paths


/**
 * Configuration of the process-global extension universe: the plugin root whose subdirectories become
 * folder scopes (null: no folder plugins, the application classpath only). Compared by value, so an identical
 * re-initialization is a no-op and a differing one is a fault. Paths are normalized to absolute form so the
 * same root spelled two ways compares equal.
 */
data class KzenAutoRuntimeConfig(
    val pluginRoot: Path?
) {
    companion object {
        /** Read by [default]: lets a test task point every context in its JVM at one constructed universe. */
        const val pluginRootSystemProperty = "kzen.plugin.root"

        val standalone = KzenAutoRuntimeConfig(null)

        /** The configuration an implicit first context creation uses: the system property, else standalone. */
        fun default(): KzenAutoRuntimeConfig {
            val property = System.getProperty(pluginRootSystemProperty)?.takeIf { it.isNotBlank() }
            return KzenAutoRuntimeConfig(property?.let { Paths.get(it) })
        }
    }

    fun normalized(): KzenAutoRuntimeConfig {
        return KzenAutoRuntimeConfig(pluginRoot?.toAbsolutePath()?.normalize())
    }
}
