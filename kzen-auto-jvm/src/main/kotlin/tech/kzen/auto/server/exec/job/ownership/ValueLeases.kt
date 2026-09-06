package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.control.ValueLease


/** Release helpers shared by every holder of several leases. */
object ValueLeases {
    /**
     * Releases every lease (nulls skipped), attempting all of them: the first close failure is thrown after the
     * rest, with the others suppressed on it.
     */
    fun releaseAll(leases: Iterable<ValueLease?>) {
        var failure: Throwable? = null
        for (lease in leases) {
            try {
                lease?.release()
            }
            catch (e: Exception) {
                if (failure == null) failure = e else failure.addSuppressed(e)
            }
        }
        failure?.let { throw it }
    }


    /** Releases every lease, attaching any close failure to [primary] as suppressed (an unwinding caller). */
    fun releaseAllSuppressed(leases: Iterable<ValueLease?>, primary: Throwable) {
        try {
            releaseAll(leases)
        }
        catch (e: Exception) {
            primary.addSuppressed(e)
        }
    }
}
