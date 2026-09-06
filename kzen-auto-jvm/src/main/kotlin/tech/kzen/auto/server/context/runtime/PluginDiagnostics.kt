package tech.kzen.auto.server.context.runtime

import tech.kzen.lib.server.reflect.AggregateClassLoader
import java.util.concurrent.ConcurrentHashMap


/**
 * Resolution-time findings the aggregate loader reports lazily, kept per scope for the diagnostics view: a
 * folder class shadowed by the application classpath (a warning; the application copy is served) and a class
 * name defined by several folder scopes (an error; resolution failed by name). Append-only and thread-safe;
 * the global scope list itself never changes.
 */
class PluginDiagnostics: AggregateClassLoader.Diagnostics {
    private val shadowedByScope = ConcurrentHashMap<PluginScopeId, MutableSet<String>>()
    private val ambiguousByScope = ConcurrentHashMap<PluginScopeId, MutableSet<String>>()


    override fun shadowed(scopeId: String, className: String) {
        shadowedByScope.computeIfAbsent(PluginScopeId(scopeId)) { ConcurrentHashMap.newKeySet() }.add(className)
    }


    override fun ambiguous(scopeIds: List<String>, className: String) {
        for (scopeId in scopeIds) {
            ambiguousByScope.computeIfAbsent(PluginScopeId(scopeId)) { ConcurrentHashMap.newKeySet() }.add(className)
        }
    }


    /** Class names of [scopeId]'s jars that the application classpath shadows, sorted. */
    fun shadowedClasses(scopeId: PluginScopeId): List<String> {
        return shadowedByScope[scopeId]?.sorted() ?: listOf()
    }


    /** Class names [scopeId] defines together with another scope, sorted. */
    fun ambiguousClasses(scopeId: PluginScopeId): List<String> {
        return ambiguousByScope[scopeId]?.sorted() ?: listOf()
    }
}
