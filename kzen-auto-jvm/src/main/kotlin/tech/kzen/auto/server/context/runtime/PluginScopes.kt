package tech.kzen.auto.server.context.runtime

import java.net.URLClassLoader


/** The discovered universe: the application scope first, then folder scopes in directory-name order. */
class PluginScopes(
    val all: List<PluginScope>
) {
    init {
        require(all.isNotEmpty() && all.first().isApplication) { "The application scope comes first" }
        val ids = all.map { it.id }
        require(ids.size == ids.toSet().size) { "Scope ids must be unique: $ids" }
    }

    val application: PluginScope
        get() = all.first()

    /** Folder scopes only, loaded or failed, in order. */
    val folders: List<PluginScope>
        get() = all.drop(1)

    val loadedFolders: List<PluginScope>
        get() = folders.filter { it.status == PluginScope.Status.LOADED }

    fun get(id: PluginScopeId): PluginScope? {
        return all.firstOrNull { it.id == id }
    }


    /**
     * Releases the folder scopes' jars, which their loaders otherwise hold open until garbage collection (on
     * Windows, blocking deleting or replacing them). Only for a universe that is not pinned: a pinned
     * universe's loaders serve the process for its lifetime.
     */
    internal fun closeFolderLoaders() {
        for (scope in loadedFolders) {
            (scope.classLoader as? URLClassLoader)?.close()
        }
    }
}
