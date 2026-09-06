package tech.kzen.auto.server.context.runtime

import java.nio.file.Path


/**
 * One plugin installation scope: the application classpath (plugin zero) or one folder of jars loaded by one
 * pinned, parent-first [URLClassLoader]. Immutable after discovery; a scope that failed to load carries its
 * named [failure] and no loader, and never hides the others.
 */
data class PluginScope(
    val id: PluginScopeId,
    val directory: Path?,
    val jars: List<Path>,
    val manifest: PluginManifest,
    val classLoader: ClassLoader?,
    val status: Status,
    val failure: String?
) {
    enum class Status { LOADED, FAILED }

    val isApplication: Boolean
        get() = id.isApplication()

    val version: String?
        get() = manifest.version

    /** The loader, or a named failure for a scope that did not load. */
    fun requireClassLoader(): ClassLoader {
        return classLoader
            ?: throw IllegalStateException("Plugin scope '$id' did not load: $failure")
    }

    companion object {
        fun application(classLoader: ClassLoader): PluginScope {
            return PluginScope(
                PluginScopeId.application, null, listOf(), PluginManifest.empty, classLoader, Status.LOADED, null)
        }
    }
}
