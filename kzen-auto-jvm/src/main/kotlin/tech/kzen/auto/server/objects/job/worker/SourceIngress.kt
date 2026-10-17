package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.api.data.Borrowed
import tech.kzen.auto.server.objects.logic.ExpressionReturnTypeInference
import tech.kzen.lib.common.exec.engine.resource.Hold
import tech.kzen.lib.common.exec.engine.resource.Holder
import tech.kzen.lib.common.exec.engine.resource.Resource
import tech.kzen.lib.common.exec.engine.resource.ResourceOwners
import tech.kzen.lib.common.exec.engine.resource.ResourceRegistry
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * The framework's source-ingress boundary (E9 item 2): where the framework owns the pull, a native is adopted
 * **inside the blocking acquisition body, before the cancellable return to the coroutine** — and recorded as the
 * body's acquisition ([BlockingAcquisitions]), so a cancel that wins the return dispatch lets the hold go
 * instead of dropping it. The producer hold taken here is released by the source after its send (the channel
 * holds from then on) or on any earlier exit, which closes the item. An opened stream's closeable iterator and
 * container are adopted the same way, held by the source's location until the source lets the stream go.
 *
 * [pull] takes up to [Emitter.batchSize] elements per blocking trip — one dispatcher hop amortized over a
 * batch of scalars — but returns as soon as it adopted a closeable: an owned item is never pulled ahead of, so
 * an arena-backed source parks on its next permit only after the item that would return it has been sent.
 * Outside a run (no registry) nothing is adopted and the source behaves as a plain pull loop.
 */
internal class SourceIngress(
    private val control: JobControl,
    selfLocation: ObjectLocation
) {
    companion object {
        /** The holder of the pull-to-send hold on an adopted item, before its channel takes over. */
        val producerHolder = Holder("producer")
    }


    private val registry: ResourceRegistry? = control.resources()
    private val streamHolder = Holder(selfLocation.asString())


    /** The run's resource registry, for what a source adopts beyond this ingress; null outside a run. */
    fun registry(): ResourceRegistry? = registry


    /** Opens a stream on the blocking dispatcher, adopting its closeable parts before returning. */
    suspend fun openStream(open: () -> Any?): OpenedStream? {
        return control.runBlockingIoAcquiring { acquired ->
            adoptStream(open())?.also { stream ->
                if (stream.closeable) {
                    acquired.record(stream::close)
                }
            }
        }
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
        if (registry == null) {
            return OpenedStream(iterator, closeables, emptyList())
        }
        val adoptions = closeables.map { closeable -> registry.adopt(closeable, closeable::close, streamHolder) }
        val owners = adoptions.fold(ResourceOwners.empty) { acc, adoption -> acc + ResourceOwners.of(adoption.resource) }
        return OpenedStream(iterator, closeables, adoptions.map { it.hold }, owners)
    }


    /**
     * Pulls the next elements of [stream] on the blocking dispatcher — up to [limit], stopping after the first
     * adopted closeable — each adopted as it is pulled. Empty when the iterator is exhausted. A [LentElement]
     * depends on the stream it was pulled from: it carries the stream's owners, so a stop that makes the source let
     * the stream go does not close it under a holder still reading the element.
     */
    suspend fun pull(stream: OpenedStream, limit: Int): List<AcquiredItem> {
        val iterator = stream.iterator
        return control.runBlockingIoAcquiring { acquired ->
            val pulled = ArrayList<AcquiredItem>()
            while (pulled.size < limit.coerceAtLeast(1) && iterator.hasNext()) {
                val element = iterator.next()
                val item = adopt(element, if (element is LentElement) stream.owners else ResourceOwners.empty)
                pulled.add(item)
                if (item.owned) {
                    acquired.record(item::release)
                    break
                }
            }
            pulled
        }
    }


    /**
     * Adopts one pulled element (inside a blocking body). A `Borrowed` native is unwrapped and carries a borrowed
     * resource instead of being adopted: the run never closes it, and a channel's hold on the lifted value finds
     * the owner already named rather than adopting the closeable itself.
     */
    fun adopt(element: Any?, inherited: ResourceOwners = ResourceOwners.empty): AcquiredItem {
        if (registry == null) {
            val native = if (element is Borrowed<*>) element.value else element
            return AcquiredItem(native, ResourceOwners.empty, Hold.none)
        }
        if (element is Borrowed<*>) {
            return AcquiredItem(element.value, ResourceOwners.of(Resource.borrowed(element.value)) + inherited, Hold.none)
        }
        if (element !is AutoCloseable) {
            return AcquiredItem(element, inherited, Hold.none)
        }
        val adoption = registry.adopt(element, element::close, producerHolder)
        return AcquiredItem(element, ResourceOwners.of(adoption.resource) + inherited, adoption.hold)
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
