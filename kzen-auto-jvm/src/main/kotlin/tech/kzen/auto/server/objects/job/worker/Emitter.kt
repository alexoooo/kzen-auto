package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl


/**
 * Emission handle a [SourceWorker] / [TransformWorker] hands to its work hooks. Every channel element is a
 * [JobMessage] (the uniform carrier), so the Worker emits single messages and never touches
 * [ChannelOutput.close] — end-of-stream propagation is owned by the framework ([WorkerBase]).
 *
 * Batching is a framework concern: [send] only buffers; the accumulated elements reach the channel (as one
 * batch) on [flush]. A TRANSFORM leaves [flush] to its drive loop (called once per input batch, after the whole
 * batch is consumed — so a parked Worker strands no received element). A SOURCE has no input-batch boundary the
 * drive loop could hook, so it enables [sourceCadence]: [send] then auto-[flush]es every [ChannelOutput.batchSize]
 * elements, checkpointing and publishing progress at that boundary — so a source still batches, stays
 * cooperatively pausable, and advances exactly one batch per step.
 */
class Emitter(
    private val output: ChannelOutput<Any?>
) {
    private var cadence: Cadence? = null
    private var sinceFlush = 0


    /**
     * Enables the source flush cadence (see class doc): after every [ChannelOutput.batchSize] [send]s, flush the
     * batch, [JobControl.checkpoint], then run [onFlush] (progress publish). Called once by [SourceWorker] before
     * [SourceWorker.produce]; a transform/sink never calls it.
     */
    internal fun sourceCadence(control: JobControl, onFlush: () -> Unit) {
        cadence = Cadence(control, onFlush)
    }


    suspend fun send(element: JobMessage) {
        output.send(element)

        val cadence = cadence
            ?: return

        sinceFlush += 1
        if (sinceFlush >= output.batchSize()) {
            output.flush()
            cadence.control.checkpoint()
            cadence.onFlush()
            sinceFlush = 0
        }
    }


    suspend fun flush() {
        output.flush()
    }


    private class Cadence(
        val control: JobControl,
        val onFlush: () -> Unit
    )
}
