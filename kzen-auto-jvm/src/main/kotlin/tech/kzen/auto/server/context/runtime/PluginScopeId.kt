package tech.kzen.auto.server.context.runtime


/**
 * Identity of a plugin installation scope. A folder scope's implicit id is its canonical directory name; a
 * manifest `id` overrides it; the application classpath carries the one reserved id. Identity is never
 * undefined, so the duplicate-id boot check is over resolved ids.
 */
@JvmInline
value class PluginScopeId(val value: String) {
    init {
        require(value.isNotBlank()) { "Plugin scope id must not be blank" }
    }

    companion object {
        val application = PluginScopeId("application")
    }

    fun isApplication(): Boolean = this == application

    override fun toString(): String = value
}
