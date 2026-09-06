package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.auto.server.exec.job.ownership.ValueLeases


/**
 * A stream container a source opened, as the framework drives it: the iterator to pull from and the run's
 * holds on whatever is closeable — the iterator, then the container, de-duplicated by identity — taken inside
 * the blocking boundary that opened it (E9 item 1). [close] releases the holds (the ledger closes the iterator
 * first, then the container, exactly once) or, outside a run, closes them directly. [closeable] tells a
 * source whether the stream must be detached across a live edit (a closeable stream is never re-opened) or
 * may be re-evaluated and skipped.
 */
class OpenedStream internal constructor(
    val iterator: Iterator<*>,
    private val closeables: List<AutoCloseable>,
    private val holds: List<ValueLease>
) {
    @Volatile
    private var closed = false


    val closeable: Boolean
        get() = closeables.isNotEmpty()


    /** Lets go of the stream: the run closes it (or, outside a run, it is closed here). Idempotent. */
    fun close() {
        if (closed) {
            return
        }
        closed = true
        if (holds.isNotEmpty()) {
            ValueLeases.releaseAll(holds)
            return
        }
        var failure: Throwable? = null
        for (closeable in closeables) {
            try {
                closeable.close()
            }
            catch (e: Exception) {
                if (failure == null) failure = e else failure.addSuppressed(e)
            }
        }
        failure?.let { throw it }
    }
}
