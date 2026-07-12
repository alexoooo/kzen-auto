package tech.kzen.auto.server.service.storage

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Deletes an area's least-recently-modified bundles until total size fits the area's
 * [ManagedStorageArea.budgetBytes]. Owners trigger [maybeEvict] after growing the area
 * (and once at boot); a no-budget area is never evicted.
 *
 * Single-flight: concurrent triggers collapse into the one in-progress sweep. The caller
 * must not hold any per-bundle lock when triggering — the area's own [deleteBundle]
 * acquires those, one bundle at a time.
 */
class StorageLruEvictor(
    private val area: ManagedStorageArea
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(StorageLruEvictor::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val evicting = AtomicBoolean()


    //-----------------------------------------------------------------------------------------------------------------
    fun maybeEvict() {
        val budget = area.budgetBytes
            ?: return

        if (!evicting.compareAndSet(false, true)) {
            return
        }

        try {
            evictToBudget(budget)
        }
        finally {
            evicting.set(false)
        }
    }


    private fun evictToBudget(budget: Long) {
        val bundles = area.bundles()
        var totalBytes = bundles.sumOf { it.sizeBytes }
        if (totalBytes <= budget) {
            return
        }

        val evictionOrder = bundles
            .filter { !it.active }
            .sortedBy { it.lastModifiedMillis }

        for (bundle in evictionOrder) {
            if (totalBytes <= budget) {
                break
            }

            val error = area.deleteBundle(bundle.key)
            if (error == null) {
                totalBytes -= bundle.sizeBytes
                logger.info("Evicted from {}: {} ({} bytes)", area.id, bundle.key, bundle.sizeBytes)
            }
            else {
                logger.warn("Unable to evict from {}: {} - {}", area.id, bundle.key, error)
            }
        }
    }
}
