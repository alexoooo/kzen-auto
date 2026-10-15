package tech.kzen.auto.server.data.design

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.util.digest.Digest


/**
 * The one way validation looks at data (docs/plans/2026-09-24_values-metadata-and-design-time-types.md, R5): each
 * validation pass opens a [DesignReadSession], bounded by a [DesignReadBudget], which records what it looked at as
 * [DesignEvidence]. What was read is kept here, keyed by the content's fingerprint, so a pass after an edit, or one
 * continuing a pass cut short by its budget, reads only what it has not read before.
 *
 * Nothing here knows files or tables: a lane offers sample values, and a Worker types itself from them through the
 * session (see [tech.kzen.auto.server.objects.job.worker.JobLaneSample]).
 */
class DesignReader {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Inspected parts across every open editor; each entry is small (a shape)
        private const val cacheSize = 16_384L
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val shapes: Cache<Digest, DataShape> = Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .build()


    //-----------------------------------------------------------------------------------------------------------------
    fun session(budget: DesignReadBudget): DesignReadSession =
        DesignReadSession(this, budget)


    internal fun cachedShape(key: Digest): DataShape? =
        shapes.getIfPresent(key)


    internal fun storeShape(key: Digest, shape: DataShape) {
        shapes.put(key, shape)
    }

}
