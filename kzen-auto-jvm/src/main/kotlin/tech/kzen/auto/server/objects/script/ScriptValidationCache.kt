package tech.kzen.auto.server.objects.script

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import tech.kzen.auto.common.objects.document.registry.ObjectRegistryConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.service.impl.LinkedLogicDocuments
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.util.digest.Digest


/**
 * Caches [ScriptValidation] per notation version, so the fixpoint (with its per-formula Kotlin compiles)
 * re-runs only when notation it depends on actually changed. Consulted by both validation call sites — the
 * editor's detached [ScriptValidator.execute] (where a hit also skips graph instantiation) and the run-compile
 * path ([tech.kzen.auto.server.exec.script.ScriptLogicCompiler.compile], including hosted-child and live-edit
 * migration recompiles) — which share entries because both key on the same full (unfiltered) definition.
 *
 * The key is a content digest over everything validation reads:
 * the root document's transitive closure PLUS each linked logic document's closure
 * ([LinkedLogicDocuments.transitiveDigest] — a RunStep's return type comes from its weakly-linked callee's
 * `results` signature, invisible to a plain per-document closure digest), PLUS every object-registry
 * document's declared class list ([ScriptValidator.validate] scans them globally for the Formula
 * type-visibility filter; classpath availability of a declared class is process-static, so the notation-level
 * class list suffices). Same contract as the live-edit migration signal: a third-party step whose
 * `definition()` read some UNRELATED document would see stale validation until its own closure changes.
 *
 * Keyed by digest (not document path) so a paused run's compile-time snapshot and the editor's current
 * version coexist; bounded LRU, entries for stale versions simply age out. A mid-edit broken graph can make
 * the closure digest uncomputable — then the compute runs uncached (matching the controller's
 * keep-running fallback). Does NOT cache the [tech.kzen.lib.common.model.instance.GraphInstance]
 * (executor-level instance caching is owned elsewhere); the cached value is a defensive copy, safe to share.
 */
class ScriptValidationCache {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Distinct (document closure × notation version) entries live at once: bounded by open editors plus
        // paused runs' snapshots, so a small cap holds the working set while stale versions age out.
        private const val validationCacheSize = 100L
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val cache: Cache<Digest, ScriptValidation> = Caffeine.newBuilder()
        .maximumSize(validationCacheSize)
        .build()


    //-----------------------------------------------------------------------------------------------------------------
    fun scriptValidation(
        documentPath: DocumentPath,
        graphDefinition: GraphDefinition,
        compute: () -> ScriptValidation
    ): ScriptValidation {
        val key = digestKey(documentPath, graphDefinition)
            ?: return compute()

        return cache.get(key) {
            ScriptValidation(compute().stepValidations.toMap())
        }
    }


    private fun digestKey(
        documentPath: DocumentPath,
        graphDefinition: GraphDefinition
    ): Digest? {
        return try {
            Digest.build {
                addDigestible(LinkedLogicDocuments.transitiveDigest(
                    graphDefinition, graphDefinition.graphStructure, documentPath))

                val documents = graphDefinition.graphStructure.graphNotation.documents.map
                for ((registryPath, documentNotation) in documents.entries.sortedBy { it.key.asString() }) {
                    if (! ObjectRegistryConventions.isObjectRegistry(documentNotation)) {
                        continue
                    }
                    addDigestible(registryPath)
                    val classNames = ObjectRegistryConventions.classesSpec(documentNotation)
                        ?.classNames
                        ?: continue
                    for (className in classNames.map { it.asString() }.sorted()) {
                        addUtf8(className)
                    }
                }
            }
        }
        catch (_: Exception) {
            null
        }
    }
}
