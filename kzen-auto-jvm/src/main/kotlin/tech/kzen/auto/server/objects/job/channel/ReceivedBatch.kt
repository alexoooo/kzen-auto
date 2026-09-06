package tech.kzen.auto.server.objects.job.channel

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * One physical batch as a framework drive loop consumes it: the elements, the channel lease each owned element
 * carries, and how far the loop has dispatched. A [TransformWorker][tech.kzen.auto.server.objects.job.worker.TransformWorker]
 * / Sink marks an element dispatched before its callback (the element is the callback's from then on, and a
 * migration re-delivers only the [remaining] ones — the channel captures them while the batch stays attached);
 * an expanding transform [detach]es the batch and carries it in its own migration state instead, marking each
 * element only once its expansion completed. Read by the run driver at a quiescent barrier, so the dispatch
 * index is volatile.
 */
internal class ReceivedBatch(
    val elements: List<DataValue>,
    private val leases: List<ValueLease?>,
    private var onDetach: (() -> Unit)?
) {
    companion object {
        /**
         * The next batch of [input] for a framework loop: through the lease-carrying path when the input is a
         * [JobChannel]'s, otherwise the SPI batch, unowned, converted element by element.
         */
        suspend fun receive(input: ChannelInput<*>, convert: (Any?) -> DataValue): ReceivedBatch? {
            if (input is FrameworkChannelInput) {
                return input.receiveFrameworkBatch()
            }
            val batch = input.receiveBatch()
                ?: return null
            val elements = batch.map(convert)
            return ReceivedBatch(elements, elements.map { null }, null)
        }
    }


    @Volatile
    private var dispatched = 0


    val size: Int
        get() = elements.size


    /** The channel's hold on the element at [index], or null for an unowned element. */
    fun channelLease(index: Int): ValueLease? = leases[index]


    /** Elements before and including [index] are the consumer's now; a migration re-delivers only those after. */
    fun markDispatched(index: Int) {
        dispatched = index + 1
    }


    fun remainingCount(): Int = size - dispatched


    /** The not-yet-dispatched tail, with its leases, for a migration's carryover. */
    fun remaining(): ChannelCarryover {
        val from = dispatched
        return ChannelCarryover(elements.subList(from, size), leases.subList(from, size))
    }


    /** Takes the batch off the channel's capture: the caller carries it (and releases its leases) from here on. */
    fun detach() {
        onDetach?.invoke()
        onDetach = null
    }


    /** Lets go of the channel holds past the dispatched index (a detached batch that is dropped). */
    fun releaseRemaining() {
        remaining().release()
    }
}
