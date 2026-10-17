package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.JobControl


/**
 * What a blocking body acquired — the holds it took inside [JobControl.runBlockingIo] — so that a return the
 * coroutine never sees still lets them go (a hold is never dropped). The body completes before the offload
 * returns or throws, so its record is complete either way: a cancel that wins the return dispatch, or a body
 * that fails after acquiring, releases every recorded acquisition in reverse order, with a close failure
 * suppressed onto the primary; a body whose value is delivered hands its acquisitions to the caller untouched.
 * Record nothing for an unowned acquisition, so the pull hot path stays allocation-free.
 */
internal class BlockingAcquisitions {
    private var releases: ArrayList<() -> Unit>? = null


    fun record(release: () -> Unit) {
        val recorded = releases
            ?: ArrayList<() -> Unit>(1).also { releases = it }
        recorded.add(release)
    }


    internal fun releaseAll(primary: Throwable) {
        val recorded = releases
            ?: return
        releases = null
        for (release in recorded.asReversed()) {
            try {
                release()
            }
            catch (e: Exception) {
                primary.addSuppressed(e)
            }
        }
    }
}


/** [JobControl.runBlockingIo] for a body that acquires as it runs: see [BlockingAcquisitions]. */
internal suspend fun <R> JobControl.runBlockingIoAcquiring(block: (BlockingAcquisitions) -> R): R {
    val acquisitions = BlockingAcquisitions()
    try {
        return runBlockingIo { block(acquisitions) }
    }
    catch (e: Throwable) {
        acquisitions.releaseAll(e)
        throw e
    }
}
