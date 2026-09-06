package tech.kzen.auto.server.context

import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.PluginScopeId
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.ClassName
import java.util.concurrent.ConcurrentHashMap


/**
 * This context's view of which plugin-contributed classes it can instantiate: initialized at creation from the
 * runtime's explicit contributions (every scope's generated registry checked against this context's
 * [GraphEnvironment]) and augmented monotonically as reflective class names are first resolved through
 * [of] (compute-if-absent, so two concurrent first references resolve once). A class whose `@Service` type
 * the environment lacks is *unavailable in this workspace*, named — never a blocked workspace, and never a
 * change to the runtime's global state. Safe to cache because the environment is fixed at creation.
 *
 * As a [LocalGraphStore.Observer] it learns from notation: after every successful command and store refresh,
 * each `class` a document names that a folder scope defines and kzen's own generated registry does not serve
 * is resolved once — the lazy, per-context path by which a notation edit first naming a reflective plugin
 * Worker shows up in this workspace's view and nowhere else.
 */
class PluginAvailability(
    private val runtime: KzenAutoRuntime,
    private val environment: GraphEnvironment
): LocalGraphStore.Observer {
    sealed interface Availability {
        data object Available: Availability

        /** The class needs a `@Service` type this context does not provide. */
        data class Unavailable(val missingServices: List<ClassName>): Availability

        /** The global mirror cannot serve the class (absent, malformed, or defined by several scopes). */
        data class Unresolvable(val reason: String): Availability
    }


    private val byClass = ConcurrentHashMap<ClassName, Availability>()
    private val definingScopesByClass = ConcurrentHashMap<ClassName, List<PluginScopeId>>()
    private val contributedByScope: Map<PluginScopeId, List<ClassName>>


    init {
        val contributed = mutableMapOf<PluginScopeId, MutableList<ClassName>>()
        for (contribution in runtime.contributions) {
            val registry = contribution.generatedRegistry ?: continue
            val declarations = registry.serviceArgumentDeclarations()
            val classes = declarations.values.flatten().toSet()
            for (className in classes) {
                byClass[className] = check(registry.serviceArguments(className).values)
                contributed.getOrPut(contribution.scopeId) { mutableListOf() }.add(className)
            }
        }
        contributedByScope = contributed.mapValues { entry -> entry.value.sortedBy { it.get() } }
    }


    /** Availability of [className] in this context, resolved once through the global mirror. */
    fun of(className: ClassName): Availability {
        return byClass.computeIfAbsent(className) { resolve(it) }
    }


    /**
     * Resolves every `class` the notation names that a folder scope defines (see the class comment); idempotent.
     * Names only the application classpath defines are not this view's business — kzen's own reflective fallbacks
     * are logged by the mirror, and type names like `kotlin.String` are not instantiables at all.
     */
    fun learn(graphNotation: GraphNotation) {
        for (objectNotation in graphNotation.coalesce.map.values) {
            val className = objectNotation.get(NotationConventions.classAttributeName)?.asString()
                ?.let { ClassName(it) }
                ?: continue
            if (byClass.containsKey(className) || ReflectionRegistry.global.contains(className)) {
                continue
            }
            if (definingScopes(className).any { it != runtime.scopes.application.id }) {
                of(className)
            }
        }
    }


    override suspend fun onCommandSuccess(
        event: NotationEvent,
        graphDefinition: GraphDefinitionAttempt,
        attachment: LocalGraphStore.Attachment
    ) {
        learn(graphDefinition.graphStructure.graphNotation)
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        learn(graphDefinitionAttempt.graphStructure.graphNotation)
    }


    /** Classes already known to this view (contributed at creation or resolved since), with their availability. */
    fun known(): Map<ClassName, Availability> {
        return byClass.toSortedMap(compareBy { it.asString() })
    }


    /** Generated-registry classes a scope contributed, as checked at creation. */
    fun contributedBy(scopeId: PluginScopeId): List<ClassName> {
        return contributedByScope[scopeId] ?: listOf()
    }


    /**
     * The scopes whose jars define [className] (several when the name is ambiguous), the application scope when
     * the application classpath does, empty when nothing does. Cached: a scope's jars never change after boot, and
     * the lookup is a jar-index probe per scope rather than a scan.
     */
    fun definingScopes(className: ClassName): List<PluginScopeId> {
        return definingScopesByClass.computeIfAbsent(className) { resolveDefiningScopes(it) }
    }


    private fun resolveDefiningScopes(className: ClassName): List<PluginScopeId> {
        val contributed = contributedByScope.filterValues { className in it }.keys
        if (contributed.isNotEmpty()) {
            return contributed.sortedBy { it.value }
        }
        val folders = runtime.aggregateClassLoader.definingScopes(className.get()).map { PluginScopeId(it.id) }
        if (folders.isNotEmpty()) {
            return folders
        }
        val resource = className.get().replace('.', '/') + ".class"
        val application = runtime.scopes.application.requireClassLoader().getResource(resource)
        return if (application != null) listOf(runtime.scopes.application.id) else listOf()
    }


    private fun resolve(className: ClassName): Availability {
        if (!GlobalMirror.contains(className)) {
            return Availability.Unresolvable("not served by any registry or mirror")
        }
        val services = try {
            GlobalMirror.serviceArguments(className).values
        }
        catch (e: IllegalArgumentException) {
            return Availability.Unresolvable(e.message ?: e.toString())
        }
        return check(services)
    }


    private fun check(services: Collection<ClassName>): Availability {
        val missing = services.filter { !environment.contains(it) }
        return if (missing.isEmpty()) Availability.Available else Availability.Unavailable(missing.sortedBy { it.asString() })
    }
}
