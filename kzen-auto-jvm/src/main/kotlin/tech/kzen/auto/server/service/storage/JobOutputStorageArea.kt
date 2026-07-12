package tech.kzen.auto.server.service.storage

import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.util.digest.Digest
import java.nio.file.Path


/**
 * Persistent Job worker output dirs (see
 * [tech.kzen.auto.server.objects.job.service.JobWorkPool.workerOutputDir]), keyed on the digest
 * of the worker's notation location. A dir whose worker no longer exists in notation (deleted
 * or renamed worker) is labelled orphaned — this area is the only way to reclaim it.
 */
class JobOutputStorageArea(
    root: Path,
    private val graphNotation: () -> GraphNotation,
    private val anyRunActive: () -> Boolean
): DirectoryStorageArea(
    "job-output",
    "Job outputs",
    "Persistent output of Job workers, kept after the run for browsing and download. " +
        "The worker re-creates it on the next run.",
    root
) {
    companion object {
        private const val orphanedSuffix = " (orphaned)"
    }


    override fun bundles(): List<StorageBundle> {
        // Reverse the workerOutputDir digest against current notation, once per enumeration.
        val labelByDigest = graphNotation().objectLocations.associateBy(
            { WorkUtils.filenameEncodeDigest(Digest.ofUtf8(it.asString())) },
            { it.asString() })

        return super.bundles().map { bundle ->
            val label = labelByDigest[bundle.key]
                ?: (bundle.key + orphanedSuffix)
            bundle.copy(displayName = label)
        }
    }


    // Coarse guard: a worker of any active logic run may hold its output file open.
    override fun bundleActive(bundleKey: String): Boolean {
        return anyRunActive()
    }
}
