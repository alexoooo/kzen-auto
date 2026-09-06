package tech.kzen.auto.server.context.runtime


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
}
