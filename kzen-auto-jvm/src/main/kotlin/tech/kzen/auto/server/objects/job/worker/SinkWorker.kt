package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A SINK Worker — consumes an input stream with no output channel (e.g. a file writer, or the live
 * [PreviewWorker]). The framework owns the drain loop, a [JobControl.checkpoint] per batch, throttled
 * progress, and — when a [serve] port is supplied — the duplex serve loop answering
 * [WorkerBase.onQuery]. The subclass implements [onBatch] (and optionally [onComplete]); each element arrives
 * as [In].
 *
 * The framework performs the single `item as In` cast here, made safe by
 * [tech.kzen.auto.common.objects.document.job.ChannelTypeDefiner] checking this worker's `in` port type
 * against the channel's element type at definition time (see TransformWorker).
 */
abstract class SinkWorker<In>(
    private val input: ChannelInput<Any?>,
    selfLocation: ObjectLocation,
    serve: ChannelServer<Any?, Any?>? = null
):
    WorkerBase(selfLocation, serve)
{
    final override suspend fun drive(control: JobControl) {
        val iterator = input.iterator()
        while (true) {
            // Checkpoint BEFORE receiving, so a parked Worker never holds a received-but-unprocessed payload:
            // at a pause wavefront every not-yet-consumed payload is still in the channel (so a migration's
            // JobChannel.drainBuffered carries it forward) rather than stranded on this Worker's stack, where
            // teardown would silently drop it.
            control.checkpoint()
            if (! iterator.hasNext()) {
                break
            }
            val item = iterator.next()

            // Safe by construction: ChannelTypeDefiner checks this port's element type at definition time.
            @Suppress("UNCHECKED_CAST")
            onBatch(item as In, control)

            publish(control)
        }
        onComplete(control)
    }


    protected abstract suspend fun onBatch(batch: In, control: JobControl)


    protected open suspend fun onComplete(control: JobControl) {}
}
