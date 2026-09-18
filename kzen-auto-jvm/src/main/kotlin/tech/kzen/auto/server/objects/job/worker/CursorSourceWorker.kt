package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.channel.DownstreamClosedException
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.reflect.full.createType


/**
 * A source Worker written without coroutines: the subclass opens an ordinary `Iterator` (from Java,
 * `Iterator<?>`) and the framework owns every pull, the lifting, batching, checkpoints, cancellation and close.
 * Each open and pull runs through [JobControl.runBlockingIo], so a pull that blocks on a host's memory budget
 * stays visible to quiescence detection. If the returned iterator is [AutoCloseable] it is closed once, on
 * completion, failure or cancellation; an item the subclass hands out is the run's from that moment on.
 *
 * Acquisition is cancellation-safe (E9 item 2): the cursor and every pulled item are adopted by the run
 * **inside** the blocking body ([SourceIngress]), so if cancellation wins the dispatch back to the coroutine
 * the acquired resource is closed by the run's teardown rather than lost; the producer hold on an item lasts
 * through lift and send and is released after the channel holds it (or on any earlier exit, which closes it).
 * Live-edit migration detaches the open iterator — with any items pulled ahead but not yet delivered — and
 * hands it to the replacement instance (no re-open, no skip); an instance that is removed by the edit has its
 * detached state closed by the engine.
 *
 * Lent elements (docs/plans/2026-09-16_borrowed-elements.md §3.2): an item that is a [LentElement] is valid
 * only until the cursor advances, so after its send — which the channel flushes at once, an owned element never
 * waiting for siblings — the source lets go of its producer hold and suspends until the item's last hold is
 * released (its close, which invalidates it), then checkpoints and only then pulls the next. Downstream
 * Workers keep draining meanwhile ([BorrowingSource]), so a pause lands between lent elements, never inside
 * one; a capture taken while lending (the run quiesced there by something other than a pause) is refused by
 * name on adoption rather than re-read. A closed downstream ([DownstreamClosedException] from the send) ends
 * the source without draining the cursor. The loop itself is [CursorLending], shared with the lending
 * transforms.
 */
abstract class CursorSourceWorker(
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    SourceWorker(output, selfLocation), BorrowingSource
{
    //-----------------------------------------------------------------------------------------------------------------
    private val lending = CursorLending(selfLocation)


    //-----------------------------------------------------------------------------------------------------------------
    /** Opens the cursor; called once per run (or not at all after a migration adopted an open one). */
    protected abstract fun open(control: JobControl): Iterator<*>


    /** Called with the current control after opening or adopting a cursor, before any pull. */
    protected open fun onCursorReady(iterator: Iterator<*>, control: JobControl) {}


    /** The element contract, when statically known; null lets each element describe itself. */
    protected open fun elementContract(): DataContract? = null


    /** The element class when the items are plain objects of one type (see [JavaTransformWorker.outputClass]). */
    protected open fun elementClass(): Class<*>? = null


    protected open fun cursorConfigurationKey(): Any? = null


    protected open fun configurationError(): String? = null


    /**
     * A declared element contract or class is the source's static output; otherwise the lane is known only at
     * run time.
     */
    final override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt =
        staticElementContract()?.let { JobLaneAttempt(JobLaneDescriptor(it), configurationError()) }
            ?: super.payloadFlow(input, context)


    private fun staticElementContract(): DataContract? =
        elementContract() ?: elementClass()?.let { JobDataValues.describe(it.kotlin.createType()) }


    /** Number of elements delivered downstream so far. */
    protected fun delivered(): Long = lending.delivered()


    /** The lent element in flight, by name, or null between lent elements. */
    protected fun lentElementName(): String? = lending.lentElementName()


    final override fun lending(): Boolean =
        lending.lending()


    final override fun awaitingRelease(): Boolean =
        lending.awaitingRelease()


    //-----------------------------------------------------------------------------------------------------------------
    final override suspend fun produce(emit: Emitter, control: JobControl) {
        try {
            lending.drain(
                control, emit, staticElementContract(),
                open = { open(control) },
                onReady = { onCursorReady(it, control) })
        }
        catch (e: DownstreamClosedException) {
            // The consumer completed: nothing more can leave here, and the cursor was closed without draining
            // what it has not read
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Migration: the open iterator and the items pulled ahead move to the replacement instance as live
    // resources; onClose then skips them here because the fields were cleared by the capture.
    final override fun captureMigrationState(): Any? =
        lending.capture(cursorConfigurationKey())


    final override fun loadMigrationState(captured: Any?) {
        lending.adopt(captured, cursorConfigurationKey())
    }
}
