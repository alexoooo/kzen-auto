package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput


/**
 * Type-safe emission handle a [SourceWorker] / [TransformWorker] hands to its work hooks. It adapts the
 * Worker's declared output element type [T] onto the erased [ChannelOutput], so the Worker emits with no cast
 * and never touches [ChannelOutput.close] — end-of-stream propagation is owned by the framework ([WorkerBase]).
 */
class Emitter<in T>(
    private val output: ChannelOutput<Any?>
) {
    suspend fun send(payload: T) {
        output.send(payload)
    }
}
