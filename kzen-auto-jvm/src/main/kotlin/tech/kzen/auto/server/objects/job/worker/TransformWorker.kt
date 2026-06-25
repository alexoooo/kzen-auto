package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A TRANSFORM Worker — maps an input stream to an output stream. The framework owns the drain loop, a
 * [JobControl.checkpoint] per batch, throttled progress, and end-of-stream (closing [output] when the input
 * ends, fails, or is cancelled). The subclass implements [onBatch] (and optionally [onComplete] to flush
 * trailing output), receiving each element as [In] and emitting via [Emitter.send].
 *
 * The framework performs the single `item as In` cast here. It is the ONE guaranteed-safe boundary: a
 * Channel declares its element type and
 * [tech.kzen.auto.common.objects.document.job.ChannelTypeDefiner] validates at definition time that this
 * worker's `in` port type matches it (and that the channel is single-reader), so a miswire is a pre-run
 * definition error rather than a ClassCastException here.
 */
abstract class TransformWorker<In, Out>(
    private val input: ChannelInput<Any?>,
    private val output: ChannelOutput<Any?>,
    selfLocation: ObjectLocation
):
    WorkerBase(selfLocation)
{
    private val emitter = Emitter<Out>(output)


    final override suspend fun drive(control: JobControl) {
        try {
            for (item in input) {
                control.checkpoint()

                // Safe by construction: ChannelTypeDefiner checks this port's element type against the
                // channel's at definition time (see the class doc).
                @Suppress("UNCHECKED_CAST")
                onBatch(item as In, emitter, control)

                publish(control)
            }
            onComplete(emitter, control)
        }
        finally {
            output.close()
        }
    }


    protected abstract suspend fun onBatch(batch: In, emit: Emitter<Out>, control: JobControl)


    protected open suspend fun onComplete(emit: Emitter<Out>, control: JobControl) {}
}
