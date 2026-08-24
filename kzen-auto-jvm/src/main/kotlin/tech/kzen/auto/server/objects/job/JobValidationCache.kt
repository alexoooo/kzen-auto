package tech.kzen.auto.server.objects.job

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.util.digest.Digest


/**
 * Caches [JobValidation] per notation version, so the payload-type walk (with its per-expression Kotlin
 * compiles) re-runs only when notation it depends on actually changed — the
 * [tech.kzen.auto.server.objects.script.ScriptValidationCache] pattern for the Job flavour. Consulted by both
 * call sites — the editor's detached [JobValidator.execute] (where a hit also skips channel synthesis and
 * graph instantiation) and the run path ([tech.kzen.auto.server.exec.job.JobRun], which threads each Worker's
 * inferred input payload type into its control) — which share entries because both key on the same full
 * (unfiltered) definition via [JobValidationDigest.documentClosureKey] (see its doc for coverage: the
 * Workers themselves, which are pruned from the definition but digested from notation, and linked callee
 * documents — a RunWorker's output type comes from its weakly-linked callee's signature — plus each
 * capability-declared nominal DataSource's structural closure).
 *
 * Keyed by digest (not document path) so a paused run's compile-time snapshot and the editor's current
 * version coexist; bounded LRU, stale versions age out. A mid-edit broken graph can make the closure digest
 * uncomputable — then the compute runs uncached. The cached value is a defensive copy, safe to share.
 */
class JobValidationCache {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Distinct (document closure × notation version) entries live at once: bounded by open editors plus
        // paused runs' snapshots, so a small cap holds the working set while stale versions age out.
        private const val validationCacheSize = 100L
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val cache: Cache<Digest, JobValidation> = Caffeine.newBuilder()
        .maximumSize(validationCacheSize)
        .build()


    //-----------------------------------------------------------------------------------------------------------------
    fun jobValidation(
        documentPath: DocumentPath,
        graphDefinition: GraphDefinition,
        compute: () -> JobValidation
    ): JobValidation {
        val key = JobValidationDigest.documentClosureKey(documentPath, graphDefinition)
            ?: return compute()

        return cache.get(key) {
            JobValidation(compute().workerValidations.toMap())
        }
    }
}
