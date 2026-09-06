package tech.kzen.auto.server.context.runtime

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.service.media.NotationMedia


/**
 * What one scope contributed through the explicit protocols: reader providers (as descriptors), bundled
 * notation (one origin-exact media, with the origin of each document), a generated reflection registry when
 * the scope shipped `ModuleReflection` providers, and the named failures met along the way (a provider whose
 * constructor threw, an unreadable identity). Immutable after discovery; a failure here never hides the scope.
 */
class ScopeContributions(
    val scopeId: PluginScopeId,
    val readers: List<ReaderProviderDescriptor>,
    val notation: NotationMedia?,
    val notationOrigins: Map<DocumentPath, String>,
    val generatedRegistry: ReflectionRegistry?,
    val moduleReflectionClasses: List<String>,
    val failures: List<String>
)
