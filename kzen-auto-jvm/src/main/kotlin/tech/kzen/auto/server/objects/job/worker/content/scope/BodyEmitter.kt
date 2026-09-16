package tech.kzen.auto.server.objects.job.worker.content.scope

import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * A body Worker's downstream inside an [EntryScopeWorker]: delivery is synchronous to the next body Worker,
 * and the last body Worker's emitter is the scope's real output channel (design §6.3 boundary).
 */
fun interface BodyEmitter {
    suspend fun send(element: DataValue)
}
