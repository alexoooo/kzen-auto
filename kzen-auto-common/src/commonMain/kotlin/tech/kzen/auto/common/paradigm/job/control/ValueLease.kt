package tech.kzen.auto.common.paradigm.job.control


/**
 * A named hold on an owned value's native resources, taken through [JobControl.retain] by a Worker that keeps
 * a value beyond the callback that delivered it (an accumulator buffering to end-of-stream, a window). While
 * held, the run does not close the resources behind the value; [release] is idempotent, and the last release
 * across all holders closes them. A lease on an unowned value is [none]: nothing to hold, never active.
 */
interface ValueLease: AutoCloseable {
    /** Whether this lease still holds; false after [release]. */
    val isActive: Boolean

    /** Lets go once; a second call does nothing. */
    fun release()

    override fun close() {
        release()
    }

    companion object {
        /** The lease of a value the run does not own: nothing to hold, nothing to release. */
        val none: ValueLease = object: ValueLease {
            override val isActive: Boolean get() = false
            override fun release() {}
        }
    }
}
