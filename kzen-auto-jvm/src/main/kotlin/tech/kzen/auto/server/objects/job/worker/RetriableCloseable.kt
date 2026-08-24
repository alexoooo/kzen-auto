package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.JobControl
import java.io.Closeable


/**
 * One-way ownership for a blocking resource whose close may fail or lose the cancellation race after the
 * blocking call returned. Ownership is cleared only after the caller observes a successful close, so the
 * Worker's final lifecycle cleanup can safely retry. A repeated close after success is a no-op.
 */
internal class RetriableCloseable<T: Closeable> {
    private var owned: T? = null


    fun attach(value: T) {
        check(owned == null) { "A closeable is already owned" }
        owned = value
    }


    fun requireOwned(): T = requireNotNull(owned)


    fun isOwned(): Boolean = owned != null


    suspend fun close(control: JobControl?) {
        val active = owned ?: return
        if (control == null) {
            active.close()
        }
        else {
            control.runBlockingIo { active.close() }
        }
        if (owned === active) {
            owned = null
        }
    }
}
