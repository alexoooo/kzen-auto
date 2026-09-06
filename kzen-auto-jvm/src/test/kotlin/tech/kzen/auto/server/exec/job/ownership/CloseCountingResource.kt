package tech.kzen.auto.server.exec.job.ownership

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger


/**
 * The E9 test resource, shared by the ownership sessions (HS15–HS18): counts closes per instance and per
 * fixture set, optionally throws on close, and records the thread and order of each close so tests can
 * assert exactly-once, ordering and cross-run behaviour.
 */
class CloseCountingResource(
    val name: String,
    private val throwOnClose: Throwable? = null,
    private val onClose: (() -> Unit)? = null
): AutoCloseable {
    companion object {
        /** Every close across all instances, in order, for ordering assertions; cleared by [reset]. */
        val closeOrder = CopyOnWriteArrayList<String>()
        val opened = AtomicInteger()
        val closed = AtomicInteger()

        fun reset() {
            closeOrder.clear()
            opened.set(0)
            closed.set(0)
        }
    }

    val closeCount = AtomicInteger()
    @Volatile var closeThread: String? = null

    init {
        opened.incrementAndGet()
    }

    val isClosed: Boolean
        get() = closeCount.get() > 0

    override fun close() {
        closeCount.incrementAndGet()
        closed.incrementAndGet()
        closeOrder += name
        closeThread = Thread.currentThread().name
        onClose?.invoke()
        throwOnClose?.let { throw it }
    }

    override fun toString(): String = "CloseCountingResource($name, closes=${closeCount.get()})"
}
