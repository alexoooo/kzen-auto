package tech.kzen.auto.server.objects.job.worker.definition

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.util.digest.Digest


sealed class WorkerDefinitionResolution {
    data class Resolved(
        val location: ObjectLocation,
        val cacheKey: Digest,
        val value: Any
    ): WorkerDefinitionResolution()


    data class Failed(
        val message: String
    ): WorkerDefinitionResolution()
}
