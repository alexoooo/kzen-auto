package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.auto.plugin.api.data.Borrowed
import tech.kzen.auto.server.exec.job.ownership.LeaseHolder
import tech.kzen.auto.server.exec.job.ownership.OwnerSet
import tech.kzen.auto.server.exec.job.ownership.RunOwnershipLedger
import tech.kzen.auto.server.objects.logic.ExpressionReturnTypeInference
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * The framework's source-ingress boundary (E9 item 2): where the framework owns the pull, a native is adopted
 * **inside the blocking acquisition body, before the cancellable return to the coroutine** — so a cancel that
 * wins the return dispatch cannot discard an acquired resource (the run's teardown closes it instead). The
 * producer hold taken here is released by the source after its send (the channel holds from then on) or on
 * any earlier exit, which closes the item. An opened stream's closeable iterator and container are adopted the
 * same way, held by the source's location until the source lets the stream go.
 *
 * [pull] takes up to [Emitter.batchSize] elements per blocking trip — one dispatcher hop amortized over a
 * batch of scalars — but returns as soon as it adopted a closeable: an owned item is never pulled ahead of, so
 * an arena-backed source parks on its next permit only after the item that would return it has been sent.
 * Outside a run (no ledger) nothing is adopted and the source behaves as a plain pull loop.
 */
internal class SourceIngress(
    private val control: JobControl,
    selfLocation: ObjectLocation
) {
    private val ledger: RunOwnershipLedger? = control.ownership()
    private val streamHolder = LeaseHolder(selfLocation.asString())


    /** The run's ledger, for lifting acquired items with their owners; null outside a run. */
    fun ledger(): RunOwnershipLedger? = ledger


    /** Opens a stream on the blocking dispatcher, adopting its closeable parts before returning. */
    suspend fun openStream(open: () -> Any?): OpenedStream? {
        return control.runBlockingIo { adoptStream(open()) }
    }


    /**
     * Adopts an already obtained stream value (call inside a blocking body that produced it): its iterator,
     * then — when a distinct object — the container, whichever is `AutoCloseable`. Null when [value] streams
     * nothing (a null stream under a nullable type).
     */
    fun adoptStream(value: Any?): OpenedStream? {
        val iterator = ExpressionReturnTypeInference.streamIterator(value)
            ?: return null
        val closeables = ArrayList<AutoCloseable>(2)
        (iterator as? AutoCloseable)?.let { closeables.add(it) }
        if (value !== iterator) {
            (value as? AutoCloseable)?.let { closeables.add(it) }
        }
        val holds = if (ledger == null) {
            emptyList()
        }
        else {
            closeables.map { closeable -> ledger.adopt(closeable, streamHolder).producerLease }
        }
        return OpenedStream(iterator, closeables, holds)
    }


    /**
     * Pulls the next elements of [iterator] on the blocking dispatcher — up to [limit], stopping after the first
     * adopted closeable — each adopted as it is pulled. Empty when the iterator is exhausted.
     */
    suspend fun pull(iterator: Iterator<*>, limit: Int): List<AcquiredItem> {
        return control.runBlockingIo {
            val pulled = ArrayList<AcquiredItem>()
            while (pulled.size < limit.coerceAtLeast(1) && iterator.hasNext()) {
                val item = adopt(iterator.next())
                pulled.add(item)
                if (item.owned) {
                    break
                }
            }
            pulled
        }
    }


    /** Adopts one pulled element (inside a blocking body). */
    fun adopt(element: Any?): AcquiredItem {
        if (ledger == null) {
            val native = if (element is Borrowed<*>) element.value else element
            return AcquiredItem(native, OwnerSet.empty, ValueLease.none)
        }
        val adoption = ledger.adopt(element, LeaseHolder.producer)
        return AcquiredItem(adoption.native, adoption.owners, adoption.producerLease)
    }


    /**
     * Skips one element a resumed source already delivered before the edit (a non-closeable stream is
     * re-evaluated). A closeable the re-evaluation constructed is closed at once: it was pulled by the
     * framework and no one will ever see it.
     */
    fun discardSkipped(element: Any?) {
        (element as? AutoCloseable)?.close()
    }
}
