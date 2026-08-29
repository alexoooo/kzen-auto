package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * Emission handle a [SourceWorker] / [TransformWorker] / [ExpandingTransformWorker] hands to its work hooks.
 * Every channel element is a
 * [DataValue] (the uniform carrier), so the Worker emits single values and never touches
 * [ChannelOutput.close] — end-of-stream propagation is owned by the framework ([WorkerBase]).
 *
 * Batching is a framework concern: [send] only buffers; the accumulated elements reach the channel (as one
 * batch) on [flush]. A TRANSFORM leaves [flush] to its drive loop (called once per input batch, after the whole
 * batch is consumed — so a parked Worker strands no received element). A SOURCE and an EXPANDING TRANSFORM have
 * no output boundary at which they can safely wait indefinitely, so they enable [flushCadence]: [send] then
 * auto-[flush]es every [ChannelOutput.batchSize] elements, checkpointing and publishing progress at that
 * boundary. [flush] resets the cadence before touching the channel, so a checkpoint always captures an empty
 * emitter even when the flush itself parked under backpressure.
 */
class Emitter(
    private val output: ChannelOutput<DataValue>
) {
    private var cadence: Cadence? = null
    private var sinceFlush = 0


    /**
     * Enables the output flush cadence (see class doc): after every [ChannelOutput.batchSize] [send]s, flush the
     * batch, [JobControl.checkpoint], then run [onFlush] (progress publish). Called once by a framework drive loop.
     */
    internal fun flushCadence(control: JobControl, onFlush: () -> Unit) {
        cadence = Cadence(control, onFlush)
    }


    suspend fun send(element: DataValue) {
        output.send(element)

        val cadence = cadence
            ?: return

        sinceFlush += 1
        if (sinceFlush >= output.batchSize()) {
            flush()
            cadence.control.checkpoint()
            cadence.onFlush()
        }
    }


    suspend fun flush() {
        sinceFlush = 0
        output.flush()
    }


    private class Cadence(
        val control: JobControl,
        val onFlush: () -> Unit
    )
}
