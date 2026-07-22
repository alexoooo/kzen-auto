package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A SINK Worker — consumes an input stream with no output channel (e.g. a file writer, or the live
 * [PreviewWorker]). The framework owns the drain loop and the batching: it drains one physical input BATCH at a
 * time, a [JobControl.checkpoint] per batch (before receiving, so a parked Worker holds no received-but-
 * unprocessed element — at a pause wavefront every not-yet-consumed element is still in the channel, carried
 * forward by [tech.kzen.auto.server.objects.job.channel.JobChannel.drainBuffered] on a migration), dispatches
 * the batch's elements to [onElement] one by one, throttled progress, and — when a [serve] port is supplied —
 * the duplex serve loop answering [WorkerBase.onQuery].
 *
 * Every channel element is a [JobMessage] (the uniform carrier), converted per element by
 * [WorkerBase.receiveMessage] with a descriptive failure on a raw element — see [TransformWorker].
 */
abstract class SinkWorker(
    private val input: ChannelInput<Any?>,
    selfLocation: ObjectLocation,
    serve: ChannelServer<Any?, Any?>? = null
):
    WorkerBase(selfLocation, serve)
{
    final override suspend fun drive(control: JobControl) {
        while (true) {
            control.checkpoint()

            val batch = input.receiveBatch()
                ?: break

            for (element in batch) {
                onElement(receiveMessage(element), control)
            }

            publish(control)
        }
        onComplete(control)
    }


    protected abstract suspend fun onElement(element: JobMessage, control: JobControl)


    protected open suspend fun onComplete(control: JobControl) {}
}
