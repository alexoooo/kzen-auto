package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * A SOURCE Worker — produces an output stream with no input (e.g. a file reader). The subclass implements only
 * [produce], emitting single elements via [Emitter.send]; the framework owns everything else:
 *
 * - **Batching + cadence.** [produce]'s [Emitter] runs the SOURCE cadence (see [Emitter.flushCadence]): it
 *   auto-flushes a batch, [JobControl.checkpoint]s, and publishes progress every `batchSize` elements. So the
 *   source needs no manual batch loop / checkpoint / publish — it just emits elements — yet it still batches for
 *   throughput, is cooperatively pausable / cancellable, and advances exactly one batch per step. (A source has
 *   no input to strand, so flushing at these boundaries keeps migration lossless without further care.)
 * - **End-of-stream.** [output] is closed when [produce] returns, fails, or is cancelled — after a final [flush]
 *   of the trailing partial batch on normal completion — so a source can never deadlock the pipeline by
 *   forgetting to close, nor drop its last sub-batch of rows.
 * - **Ownership (E9).** An `AutoCloseable` [produce] emits becomes the run's to close (adopted at the send)
 *   and is flushed at once; one the run must not close is wrapped as [tech.kzen.auto.plugin.api.data.Borrowed].
 *   Before the send the resource is the author's: a [produce] body that acquires a native and fails before
 *   emitting it closes it on its own failure path — kzen cannot intercept an acquisition it did not make. A
 *   source that lets the framework pull ([CursorSourceWorker]) gets the guarantee from the pull itself.
 */
abstract class SourceWorker(
    private val output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    WorkerBase(selfLocation)
{
    private val emitter = Emitter(output)


    final override suspend fun drive(control: JobControl) {
        emitter.flushCadence(control) { publish(control) }
        try {
            // Leading checkpoint so a pre-armed step / pause lands the source at its first wavefront BEFORE it
            // produces anything (symmetric with a Transform / Sink parking at its loop-top checkpoint before its
            // first receive) — "nothing drained yet" at the first quiescent wavefront.
            control.checkpoint()
            produce(emitter, control)
            emitter.flush()
        }
        finally {
            output.close()
        }
    }


    protected abstract suspend fun produce(emit: Emitter, control: JobControl)
}
