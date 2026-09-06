package tech.kzen.auto.server.objects.plugin

import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.objects.document.plugin.model.PluginClassDetail
import tech.kzen.auto.common.objects.document.plugin.model.PluginDocumentDetail
import tech.kzen.auto.common.objects.document.plugin.model.PluginScopeDetail
import tech.kzen.auto.server.context.PluginAvailability
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.PluginScope
import tech.kzen.auto.server.context.runtime.PluginScopeId
import tech.kzen.auto.server.context.runtime.PluginScopes
import tech.kzen.auto.server.context.runtime.ScopeContributions
import tech.kzen.lib.platform.ClassName


/**
 * The Plugin document's read-only view over the process-global runtime and one context's availability: every
 * scope row is built from state discovered once at initialization ([KzenAutoRuntime.scopes],
 * [KzenAutoRuntime.contributions]) plus the two append-only records ([KzenAutoRuntime.diagnostics],
 * [PluginAvailability.known]). Nothing here scans a jar, instantiates a provider or touches the collector: a
 * repeated read is a repeated projection of cached state. The lower-level [scopeDetails] serves the
 * compatibility kit's inspect mode, which has discovery results but no runtime.
 */
object PluginUniverseView {
    fun scopes(runtime: KzenAutoRuntime, availability: PluginAvailability): List<PluginScopeDetail> {
        val classesByScope = classesByScope(runtime, availability)
        return scopeDetails(
            runtime.scopes,
            runtime.contributions,
            classesByScope,
            shadowed = { runtime.diagnostics.shadowedClasses(it) },
            ambiguous = { runtime.diagnostics.ambiguousClasses(it) })
    }


    fun scopeDetails(
        scopes: PluginScopes,
        contributions: List<ScopeContributions>,
        classesByScope: Map<PluginScopeId, List<PluginClassDetail>>,
        shadowed: (PluginScopeId) -> List<String>,
        ambiguous: (PluginScopeId) -> List<String>
    ): List<PluginScopeDetail> {
        val contributionsById = contributions.associateBy { it.scopeId }
        return scopes.all.map { scope ->
            scopeDetail(scope, contributionsById[scope.id], classesByScope[scope.id] ?: listOf(),
                shadowed(scope.id), ambiguous(scope.id))
        }
    }


    fun identity(identity: ReaderCapabilityIdentity): String {
        return identity.namespace + "." + identity.name + "@" + identity.compatibility
    }


    fun classDetail(className: ClassName, availability: PluginAvailability.Availability): PluginClassDetail {
        return when (availability) {
            PluginAvailability.Availability.Available ->
                PluginClassDetail(className.asString(), PluginClassDetail.available, null)

            is PluginAvailability.Availability.Unavailable ->
                PluginClassDetail(className.asString(), PluginClassDetail.unavailable,
                    "needs @Service " + availability.missingServices.joinToString { it.asString() })

            is PluginAvailability.Availability.Unresolvable ->
                PluginClassDetail(className.asString(), PluginClassDetail.unresolvable, availability.reason)
        }
    }


    private fun scopeDetail(
        scope: PluginScope,
        contributions: ScopeContributions?,
        classes: List<PluginClassDetail>,
        shadowed: List<String>,
        ambiguous: List<String>
    ): PluginScopeDetail {
        return PluginScopeDetail(
            id = scope.id.value,
            version = scope.version,
            spiVersion = scope.manifest.spiVersion?.toString(),
            directory = scope.directory?.toString(),
            jars = scope.jars.map { it.fileName.toString() },
            loaded = scope.status == PluginScope.Status.LOADED,
            failure = scope.failure,
            readers = contributions?.readers?.map { identity(it.identity) } ?: listOf(),
            documents = contributions?.notationOrigins
                ?.map { (path, origin) -> PluginDocumentDetail(path.asString(), origin) }
                ?.sortedBy { it.path }
                ?: listOf(),
            generatedModules = contributions?.moduleReflectionClasses ?: listOf(),
            classes = classes.sortedBy { it.className },
            shadowedClasses = shadowed,
            ambiguousClasses = ambiguous,
            failures = contributions?.failures ?: listOf())
    }


    /** Every class the workspace has resolved, under the scope that defines it (the application when unknown). */
    private fun classesByScope(
        runtime: KzenAutoRuntime,
        availability: PluginAvailability
    ): Map<PluginScopeId, List<PluginClassDetail>> {
        val byScope = mutableMapOf<PluginScopeId, MutableList<PluginClassDetail>>()
        for ((className, known) in availability.known()) {
            val detail = classDetail(className, known)
            val scopeIds = availability.definingScopes(className).ifEmpty { listOf(runtime.scopes.application.id) }
            for (scopeId in scopeIds) {
                byScope.getOrPut(scopeId) { mutableListOf() }.add(detail)
            }
        }
        return byScope
    }
}
