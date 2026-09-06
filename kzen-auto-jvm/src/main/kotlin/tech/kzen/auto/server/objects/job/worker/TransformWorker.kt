package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.channel.ReceivedBatch
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.exec.data.value.DataValue


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
 * keeps a mid-stream migration lossless at the batch grain. An element the run OWNS (E9) is flushed the moment
 * it is sent, so a callback can also park mid-batch under backpressure: an element counts as consumed when it
 * is dispatched, and the channel captures the not-yet-dispatched remainder of the active batch — the parked
 * element's own output is already in the output channel's parked batch. A subclass that suspends inside
 * [onElement] on anything but its final send therefore needs [ExpandingTransformWorker].
 *
 * OWNERSHIP (E9): an owned element is held for the duration of [onElement] — the channel's hold is converted
 * into this Worker's per-callback hold ([CallbackLeases]) — and released when the callback returns; a subclass
 * that keeps an element beyond the callback (an accumulator) takes an explicit [JobControl.retain] lease, and
 * anything else it needs later it copies (a scalar carries no owner). An `AutoCloseable` it emits becomes the
 * run's to close; one it does not own it wraps as [tech.kzen.auto.plugin.api.data.Borrowed].
 *
 * Every channel element is a [DataValue], so the framework has one canonical runtime carrier. A channel's declared element
 * type / a port's `of:` describe the message's PAYLOAD type, cross-checked at definition time by
 * [tech.kzen.auto.common.objects.document.job.ChannelTypeDefiner].
 *
 * An optional [serve] duplex port makes a passthrough transform LIVE-QUERYABLE — a Worker that accumulates a
 * side-summary as records flow through it (e.g. [SummaryWorker]) forwards each record downstream unchanged AND
 * answers on-demand queries against its accumulated [snapshot], exactly as [SinkWorker] does. Behaviour-
 * preserving for a plain transform: it defaults to null (no serve loop), so [FilterWorker] / [FormulaWorker] /
 * [RunWorker] are unchanged.
 *
 * Use [ExpandingTransformWorker] when one input can emit more than one output batch: it carries the active
 * physical input batch and element index across checkpoints instead of relying on the channel's buffered-input
 * recovery. This class deliberately retains its original whole-input-batch boundary and behaviour.
 */
abstract class TransformWorker(
    private val input: ChannelInput<*>,
    private val output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation,
    serve: ChannelServer<Any?, Any?>? = null
):
    WorkerBase(selfLocation, serve)
{
    private val emitter = Emitter(output)


    final override suspend fun drive(control: JobControl) {
        emitter.attach(control)
        try {
            while (true) {
                // Checkpoint with the output flushed (bottom of the previous iteration) and the previous input
                // batch fully consumed, so a parked Worker strands nothing — see the class doc.
                control.checkpoint()

                val batch = ReceivedBatch.receive(input, ::receiveValue)
                    ?: break

                for (index in 0 until batch.size) {
                    val element = batch.elements[index]
                    batch.markDispatched(index)
                    CallbackLeases.transferring(control, element, batch.channelLease(index)) {
                        onElement(element, emitter, control)
                    }
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


    protected abstract suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl)


    protected open suspend fun onComplete(emit: Emitter, control: JobControl) {}
}
