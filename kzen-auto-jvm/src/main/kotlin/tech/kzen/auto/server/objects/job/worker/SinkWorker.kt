package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.channel.ReceivedBatch
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * A SINK Worker — consumes an input stream with no output channel (e.g. a file writer, or the live
 * [PreviewWorker]). The framework owns the drain loop and the batching: it drains one physical input BATCH at a
 * time, a [JobControl.checkpoint] per batch (before receiving, so a parked Worker holds no received-but-
 * unprocessed element — at a pause wavefront every not-yet-consumed element is still in the channel, carried
 * forward by [tech.kzen.auto.server.objects.job.channel.JobChannel.drainBuffered] on a migration), dispatches
 * the batch's elements to [onElement] one by one, throttled progress, and — when a [serve] port is supplied —
 * the duplex serve loop answering [WorkerBase.onQuery].
 *
 * OWNERSHIP (E9): an owned element is held for the duration of [onElement] (the channel's hold becomes this
 * Worker's per-callback hold) and released — closed, when this was the last holder — when the callback
 * returns; a sink that keeps an element beyond the callback takes an explicit [JobControl.retain] lease, and a
 * result boundary snapshots rather than keeps the native. Every channel element is a [DataValue]; see
 * [TransformWorker].
 */
abstract class SinkWorker(
    private val input: ChannelInput<*>,
    selfLocation: ObjectLocation,
    serve: ChannelServer<Any?, Any?>? = null
):
    WorkerBase(selfLocation, serve)
{
    final override suspend fun drive(control: JobControl) {
        while (true) {
            control.checkpoint()

            val batch = ReceivedBatch.receive(input, ::receiveValue)
                ?: break

            for (index in 0 until batch.size) {
                val element = batch.elements[index]
                batch.markDispatched(index)
                CallbackLeases.transferring(control, element, batch.channelLease(index)) {
                    onElement(element, control)
                }
            }

            publish(control)
        }
        onComplete(control)
    }


    protected abstract suspend fun onElement(element: DataValue, control: JobControl)


    protected open suspend fun onComplete(control: JobControl) {}
}
