package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
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
 */
abstract class CursorSourceWorker(
    output: ChannelOutput<DataValue>,
    private val selfLocation: ObjectLocation
):
    SourceWorker(output, selfLocation)
{
    private var stream: OpenedStream? = null
    private var pending: ArrayDeque<AcquiredItem> = ArrayDeque()
    private var delivered = 0L


    /** Opens the cursor; called once per run (or not at all after a migration adopted an open one). */
    protected abstract fun open(control: JobControl): Iterator<*>


    /** The element contract, when statically known; null lets each element describe itself. */
    protected open fun elementContract(): DataContract? = null

    /** The element class when the items are plain objects of one type (see [JavaTransformWorker.outputClass]). */
    protected open fun elementClass(): Class<*>? = null


    /** A declared element contract or class is the source's static output; otherwise the lane is known only at run time. */
    final override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt =
        staticElementContract()?.let { JobLaneAttempt(JobLaneDescriptor(it), null) }
            ?: super.payloadFlow(input, context)


    private fun staticElementContract(): DataContract? =
        elementContract() ?: elementClass()?.let { JobDataValues.describe(it.kotlin.createType()) }


    /** Number of elements delivered downstream so far. */
    protected fun delivered(): Long = delivered


    final override suspend fun produce(emit: Emitter, control: JobControl) {
        val ingress = SourceIngress(control, selfLocation)
        val opened = stream
            ?: (ingress.openStream { open(control) } ?: throw IllegalStateException("open returned no iterator"))
                .also { stream = it }
        try {
            while (true) {
                if (pending.isEmpty()) {
                    val pulled = ingress.pull(opened.iterator, emit.batchSize())
                    if (pulled.isEmpty()) {
                        break
                    }
                    pending.addAll(pulled)
                }
                // Claimed before the send: a send parked mid-flush holds the value in the channel's in-flight
                // batch, which a migration carries — the resumed instance must not deliver it again
                val item = pending.removeFirst()
                delivered += 1
                try {
                    emit.send(item.lift(ingress.ledger(), staticElementContract()))
                }
                finally {
                    item.release()
                }
            }
        }
        finally {
            closeStream()
        }
    }


    private fun closeStream() {
        val open = stream ?: return
        stream = null
        val undelivered = pending
        pending = ArrayDeque()
        try {
            undelivered.forEach { it.release() }
        }
        finally {
            open.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Migration: the open iterator and the items pulled ahead move to the replacement instance as live
    // resources; onClose then skips them here because the fields were cleared by the capture.
    final override fun captureMigrationState(): Any? {
        val open = stream ?: return null
        stream = null
        val carried = pending
        pending = ArrayDeque()
        return DetachedCursor(open, carried, delivered)
    }


    final override fun loadMigrationState(captured: Any?) {
        val detached = captured as? DetachedCursor
        if (detached == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        val adopted = detached.adopt()
        stream = adopted.first
        pending = adopted.second
        delivered = detached.delivered
    }


    private class DetachedCursor(
        private var stream: OpenedStream?,
        private var pending: ArrayDeque<AcquiredItem>?,
        val delivered: Long
    ): AutoCloseable {
        fun adopt(): Pair<OpenedStream, ArrayDeque<AcquiredItem>> {
            val adopted = stream ?: throw IllegalStateException("Detached cursor already adopted or closed")
            val items = pending ?: ArrayDeque()
            stream = null
            pending = null
            return adopted to items
        }


        override fun close() {
            val open = stream ?: return
            val items = pending ?: ArrayDeque()
            stream = null
            pending = null
            try {
                items.forEach { it.release() }
            }
            finally {
                open.close()
            }
        }
    }
}
