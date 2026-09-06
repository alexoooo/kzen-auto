package tech.kzen.auto.server.context.runtime

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.media.UnionNotationMedia
import tech.kzen.lib.server.reflect.AggregateClassLoader
import tech.kzen.lib.server.reflect.ReflectiveClassMirror
import java.net.URLClassLoader
import java.nio.file.Path


/**
 * The process-global, startup-pinned extension universe: the plugin scopes, their class loaders, the aggregate
 * loader and compilation classpath over them, one reflective mirror over the aggregate, and what each scope
 * contributed through the explicit protocols (reader provider descriptors, bundled notation with exact origins,
 * scope-owned generated registries). Exactly one per JVM, initialized once before any
 * [tech.kzen.auto.server.context.KzenAutoContext] — explicitly by `KzenAutoMain` or an embedding host through
 * [initialize], or implicitly by the first context through [currentOrDefault]. A second [initialize] with an
 * equal configuration is a no-op returning the same instance; a differing one fails naming both configurations.
 * There is no unload or reset: restart to upgrade a plugin. Contexts own graph, host services, work roots,
 * controller and server; nothing of theirs lives here, and each context instantiates its own capability
 * instances from the descriptors held here. Global discovery state is immutable after initialization; only
 * [diagnostics] (lazy resolution findings) is appended to.
 */
class KzenAutoRuntime private constructor(
    val config: KzenAutoRuntimeConfig,
    val scopes: PluginScopes,
    val contributions: List<ScopeContributions>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(KzenAutoRuntime::class.java)
        private val lock = Any()

        @Volatile
        private var instance: KzenAutoRuntime? = null


        /** Pins the universe for the process, or returns the existing one when [config] equals its configuration. */
        fun initialize(config: KzenAutoRuntimeConfig): KzenAutoRuntime {
            val normalized = config.normalized()
            synchronized(lock) {
                val existing = instance
                if (existing != null) {
                    if (existing.config == normalized) {
                        return existing
                    }
                    throw PluginBootException(listOf(
                        "KzenAutoRuntime is already initialized with ${existing.config}; " +
                            "a second initialization asked for $normalized"))
                }
                val scopes = PluginScopeDiscovery.discover(normalized.pluginRoot, ClassLoaderUtils.applicationClassLoader())
                val contributions = PluginContributionDiscovery.discover(scopes)
                val runtime = KzenAutoRuntime(normalized, scopes, contributions)
                runtime.registerMirrors()
                instance = runtime
                runtime.logScopes()
                return runtime
            }
        }


        /** The initialized runtime, or the one an implicit first context creation pins from [KzenAutoRuntimeConfig.default]. */
        fun currentOrDefault(): KzenAutoRuntime {
            return instance ?: initialize(KzenAutoRuntimeConfig.default())
        }


        /** The initialized runtime; a named failure before initialization. */
        fun current(): KzenAutoRuntime {
            return instance
                ?: throw IllegalStateException("KzenAutoRuntime is not initialized")
        }


        fun isInitialized(): Boolean {
            return instance != null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** Lazy resolution findings: shadowed folder classes, names defined by several scopes. */
    val diagnostics = PluginDiagnostics()

    /** Live contexts' work-root claims, so no two contexts in this process sweep or reuse one root. */
    val workRoots = WorkRootRegistry()

    /**
     * Application classpath first, then each loaded folder scope's own loader: the loader every compiled
     * expression, reflective mirror and dynamic definer resolves through (see [ClassLoaderUtils.dynamicParentClassLoader]).
     */
    val aggregateClassLoader: AggregateClassLoader = AggregateClassLoader(
        scopes.application.requireClassLoader(),
        scopes.loadedFolders.map { AggregateClassLoader.Scope(it.id.value, it.requireClassLoader() as URLClassLoader) },
        diagnostics)

    /** One mirror over the aggregate: serves `@Reflect` classes of every scope, ambiguity by name. */
    private val aggregateMirror = ReflectiveClassMirror(aggregateClassLoader)


    /** Every scope's bundled notation as one read-only media (disjoint by the boot-time duplicate check). */
    val bundledNotation: NotationMedia by lazy {
        runBlocking {
            UnionNotationMedia.of(contributions.mapNotNull { it.notation })
        }
    }


    /**
     * The explicit union of every loaded folder scope's jars, in scope then jar order — what the expression
     * compiler adds to its classpath, since a classpath derived from a class loader cannot see through the
     * delegating aggregate.
     */
    fun pluginClasspath(): List<Path> {
        return scopes.loadedFolders.flatMap { it.jars }
    }


    /** Reader provider descriptors of every loaded scope, application first. */
    fun readerDescriptors(): List<ReaderProviderDescriptor> {
        return contributions.flatMap { it.readers }
    }


    fun contributions(scopeId: PluginScopeId): ScopeContributions {
        return contributions.firstOrNull { it.scopeId == scopeId }
            ?: throw IllegalArgumentException("Unknown plugin scope: $scopeId")
    }


    // Chain order on GlobalMirror: kzen's generated registry, then each folder's generated registry (inserted
    // ahead of any reflective mirror whichever bootstrap registered one first), then the reflective mirrors —
    // the application one KzenAutoContext registers, and this aggregate one, which alone can see folder classes.
    private fun registerMirrors() {
        for (contribution in contributions) {
            contribution.generatedRegistry?.let { GlobalMirror.registerAfterGlobalRegistry(it) }
        }
        GlobalMirror.register(aggregateMirror)
    }


    private fun logScopes() {
        logger.info("Plugin universe pinned: root={}, folder scopes={}",
            config.pluginRoot ?: "(none)", scopes.folders.size)
        for (scope in scopes.folders) {
            when (scope.status) {
                PluginScope.Status.LOADED -> {
                    val contribution = contributions(scope.id)
                    logger.info("Plugin scope '{}' loaded from {} ({} jars, version {}): {} readers, {} documents, {} generated modules",
                        scope.id, scope.directory, scope.jars.size, scope.version ?: "unspecified",
                        contribution.readers.size, contribution.notationOrigins.size,
                        contribution.moduleReflectionClasses.size)
                    for (failure in contribution.failures) {
                        logger.warn("Plugin scope '{}': {}", scope.id, failure)
                    }
                }

                PluginScope.Status.FAILED ->
                    logger.warn("Plugin scope '{}' at {} failed to load: {}", scope.id, scope.directory, scope.failure)
            }
        }
    }
}
