package tech.kzen.auto.server.context.runtime

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.plugin.api.data.ReaderCapability
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.server.notation.ClasspathNotationMedia
import tech.kzen.lib.server.notation.OriginNotationMedia
import java.util.ServiceLoader


/**
 * Runs the three contribution protocols over every loaded scope, once at boot:
 *
 * 1. `ServiceLoader` for [ReaderCapability], **de-duplicated by declaring loader** — a folder loader's
 *    `ServiceLoader` also yields every provider its parent defines, so only providers whose class the scope's own
 *    loader defined count for that scope. Each provider is instantiated once to read its identity; a duplicate
 *    identity across scopes is a boot error, a throwing constructor is a named failure on its scope.
 * 2. Bundled notation, **exact-origin**: a folder scope is scanned over its own jar URLs only
 *    ([OriginNotationMedia]) and the application scope over the application loader ([ClasspathNotationMedia]);
 *    a logical document path shipped by two origins is a boot error naming both.
 * 3. `ServiceLoader` for [ModuleReflection]: a Kotlin plugin's KSP-generated registrations go into a registry
 *    **owned by that scope** (never [ReflectionRegistry.global], whose boot validation would then block every
 *    workspace lacking the plugin's services).
 */
object PluginContributionDiscovery {
    fun discover(scopes: PluginScopes): List<ScopeContributions> {
        val errors = mutableListOf<String>()
        val contributions = mutableListOf<ScopeContributions>()
        for (scope in scopes.all) {
            if (scope.status != PluginScope.Status.LOADED) {
                contributions.add(ScopeContributions(scope.id, listOf(), null, mapOf(), null, listOf(), listOf()))
                continue
            }
            contributions.add(discoverScope(scope))
        }

        checkDuplicateReaderIdentities(contributions, errors)
        checkDuplicateNotationPaths(contributions, errors)

        if (errors.isNotEmpty()) {
            throw PluginBootException(errors)
        }
        return contributions
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun discoverScope(scope: PluginScope): ScopeContributions {
        val loader = scope.requireClassLoader()
        val failures = mutableListOf<String>()
        val readers = mutableListOf<ReaderProviderDescriptor>()

        for (provider in ServiceLoader.load(ReaderCapability::class.java, loader).stream().toList()) {
            val providerClass = provider.type()
            if (providerClass.classLoader !== loader) {
                continue
            }
            val identity: ReaderCapabilityIdentity = try {
                provider.get().identity
            }
            catch (e: Throwable) {
                failures.add("reader provider ${providerClass.name} failed to construct or report its identity: "
                    + causeChain(e))
                continue
            }
            readers.add(ReaderProviderDescriptor(scope.id, providerClass, identity) { provider.get() })
        }

        val exclude = listOf(AutoConventions.autoMainDocumentNesting)
        val notation: NotationMedia
        val origins: Map<DocumentPath, String>
        if (scope.isApplication) {
            notation = ClasspathNotationMedia(exclude = exclude, loader = loader)
            origins = runBlocking { notation.scan() }.documents.map.keys.associateWith { "application classpath" }
        }
        else {
            val origin = OriginNotationMedia(scope.jars.map { it.toUri().toURL() }, exclude = exclude)
            notation = origin
            origins = origin.origins().mapValues { it.value.toString() }
        }

        var generated: ReflectionRegistry? = null
        val moduleReflections = mutableListOf<String>()
        if (!scope.isApplication) {
            for (provider in ServiceLoader.load(ModuleReflection::class.java, loader).stream().toList()) {
                if (provider.type().classLoader !== loader) {
                    continue
                }
                try {
                    val registry = generated ?: ReflectionRegistry().also { generated = it }
                    provider.get().register(registry)
                    moduleReflections.add(provider.type().name)
                }
                catch (e: Throwable) {
                    failures.add("module reflection ${provider.type().name} failed to register: " + causeChain(e))
                }
            }
        }

        return ScopeContributions(scope.id, readers, notation, origins, generated, moduleReflections, failures)
    }


    // ServiceLoader wraps a provider's own failure in a ServiceConfigurationError; the plugin author's message is
    // the cause, so the whole chain is named.
    private fun causeChain(failure: Throwable): String {
        return generateSequence(failure) { it.cause?.takeIf { cause -> cause !== it } }
            .joinToString(" <- ") { it.javaClass.simpleName + (it.message?.let { m -> ": $m" } ?: "") }
    }


    private fun checkDuplicateReaderIdentities(contributions: List<ScopeContributions>, errors: MutableList<String>) {
        val byIdentity = contributions
            .flatMap { it.readers }
            .groupBy { it.identity }
        for ((identity, providers) in byIdentity) {
            if (providers.size > 1) {
                errors.add("Reader identity $identity is provided ${providers.size} times: " + providers.joinToString())
            }
        }
    }


    private fun checkDuplicateNotationPaths(contributions: List<ScopeContributions>, errors: MutableList<String>) {
        val byPath = mutableMapOf<DocumentPath, MutableList<String>>()
        for (contribution in contributions) {
            for ((path, origin) in contribution.notationOrigins) {
                byPath.getOrPut(path) { mutableListOf() }.add("scope '${contribution.scopeId}' ($origin)")
            }
        }
        for ((path, origins) in byPath) {
            if (origins.size > 1) {
                errors.add("Notation document $path is shipped by ${origins.size} origins: " + origins.joinToString())
            }
        }
    }
}
