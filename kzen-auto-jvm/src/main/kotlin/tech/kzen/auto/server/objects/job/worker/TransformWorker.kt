package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A TRANSFORM Worker — maps an input stream to an output stream. The framework owns the drain loop and the
 * batching: it drains one physical input BATCH at a time, dispatches its elements to [onElement] one by one
 * (which emit via [Emitter.send], buffered), and [Emitter.flush]es the accumulated output once the whole input
 * batch is consumed. The subclass sees single elements and never hand-rolls batching.
 *
 * The [JobControl.checkpoint] sits at the TOP of the loop (before receiving a batch) with the output already
 * flushed, so a parked Worker holds NEITHER a received-but-unforwarded input element (the previous batch is
 * fully consumed) NOR a buffered-but-unflushed output element — every not-yet-consumed input is still in the
 * channel (carried forward by [tech.kzen.auto.server.objects.job.channel.JobChannel.drainBuffered] on a
 * migration) and every produced element is in the output channel or its parked-mid-flush batch. This is what
 * keeps a mid-stream migration lossless at the batch grain.
 *
 * The framework performs the single `item as In` cast here. It is the ONE guaranteed-safe boundary: a Channel
 * declares its element type and [tech.kzen.auto.common.objects.document.job.ChannelTypeDefiner] validates at
 * definition time that this worker's `in` port type matches it (and that the channel is single-reader), so a
 * miswire is a pre-run definition error rather than a ClassCastException here.
 *
 * An optional [serve] duplex port makes a passthrough transform LIVE-QUERYABLE — a Worker that accumulates a
 * side-summary as records flow through it (e.g. [SummaryWorker]) forwards each record downstream unchanged AND
 * answers on-demand queries against its accumulated [snapshot], exactly as [SinkWorker] does. Behaviour-
 * preserving for a plain transform: it defaults to null (no serve loop), so [FilterWorker] / [FormulaWorker] /
 * [RunWorker] are unchanged.
 */
abstract class TransformWorker<In, Out>(
    private val input: ChannelInput<Any?>,
    private val output: ChannelOutput<Any?>,
    selfLocation: ObjectLocation,
    serve: ChannelServer<Any?, Any?>? = null
):
    WorkerBase(selfLocation, serve)
{
    private val emitter = Emitter<Out>(output)


    final override suspend fun drive(control: JobControl) {
        try {
            while (true) {
                // Checkpoint with the output flushed (bottom of the previous iteration) and the previous input
                // batch fully consumed, so a parked Worker strands nothing — see the class doc.
                control.checkpoint()

                val batch = input.receiveBatch()
                    ?: break

                for (element in batch) {
                    // Safe by construction: ChannelTypeDefiner checks this port's element type at definition time.
                    @Suppress("UNCHECKED_CAST")
                    onElement(element as In, emitter, control)
                }

                // Send this input batch's whole output as one batch (park-on-backpressure is safe now: the input
                // batch is fully consumed, so nothing is stranded on the stack).
                emitter.flush()
                publish(control)
            }
            onComplete(emitter, control)
            emitter.flush()
        }
        finally {
            output.close()
        }
    }


    protected abstract suspend fun onElement(element: In, emit: Emitter<Out>, control: JobControl)


    protected open suspend fun onComplete(emit: Emitter<Out>, control: JobControl) {}
}
