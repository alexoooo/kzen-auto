package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.channel.DownstreamClosedException
import tech.kzen.auto.server.objects.job.channel.FrameworkChannelInput
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
 * EARLY COMPLETION (content streaming spike CS2): a subclass ends its own stream via [requestCompletion], and a
 * closed downstream ([tech.kzen.auto.server.objects.job.channel.DownstreamClosedException] from the emitter) ends
 * it from outside; either way the Worker closes its input on the consumer side so the closure travels upstream.
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

    // Early completion (content streaming spike CS2): set by requestCompletion, read by the drive loop.
    private var completionRequested = false


    final override suspend fun drive(control: JobControl) {
        emitter.attach(control)
        var downstreamClosed = false
        var active: ReceivedBatch? = null
        try {
            try {
                while (!completionRequested) {
                    // Checkpoint with the output flushed (bottom of the previous iteration) and the previous
                    // input batch fully consumed, so a parked Worker strands nothing — see the class doc.
                    control.checkpoint()

                    val batch = ReceivedBatch.receive(input, ::receiveValue)
                        ?: break
                    active = batch

                    for (index in 0 until batch.size) {
                        val element = batch.elements[index]
                        batch.markDispatched(index)
                        CallbackLeases.transferring(control, element, batch.channelLease(index)) {
                            onElement(element, emitter, control)
                        }
                        if (completionRequested) {
                            break
                        }
                    }

                    if (completionRequested) {
                        // Done mid-batch: the undispatched remainder is nobody's now — release its leases.
                        abandon(batch)
                        active = null
                        break
                    }
                    active = null

                    // Send this input batch's whole output as one batch (park-on-backpressure is safe now: the
                    // input batch is fully consumed, so nothing is stranded on the stack).
                    emitter.flush()
                    publish(control)
                }
                onComplete(emitter, control)
                emitter.flush()
            }
            catch (e: DownstreamClosedException) {
                // The consumer of this Worker's output completed: nothing more can leave here, so the
                // completion emit is skipped and the closure is passed on to this Worker's own producer below.
                downstreamClosed = true
                active?.let(::abandon)
            }
        }
        finally {
            try {
                if (completionRequested || downstreamClosed) {
                    (input as? FrameworkChannelInput)?.closeConsumer()
                }
            }
            finally {
                output.close()
            }
        }
    }


    /**
     * Asks the drive loop to stop after the element currently being dispatched (content streaming spike CS2):
     * the rest of the active batch is released, [onComplete] still runs, the output closes, and this Worker's
     * input is closed on the consumer side so its producer learns the downstream is gone
     * ([DownstreamClosedException]) rather than parking behind it.
     */
    protected fun requestCompletion() {
        completionRequested = true
    }


    /** A batch this Worker stops dispatching: its undispatched elements' channel leases are released. */
    private fun abandon(batch: ReceivedBatch) {
        batch.releaseRemaining()
        batch.detach()
    }


    protected abstract suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl)


    protected open suspend fun onComplete(emit: Emitter, control: JobControl) {}
}
