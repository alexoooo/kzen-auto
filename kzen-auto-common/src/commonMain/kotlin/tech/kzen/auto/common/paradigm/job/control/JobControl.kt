package tech.kzen.auto.common.paradigm.job.control

import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Job-level cooperative synchronization handle, shared by every Worker in a running Job. Generalizes the
 * single-threaded LogicControl to the concurrent setting: Workers run at full speed in parallel and only
 * coordinate at the safe points they declare by calling [checkpoint].
 */
interface JobControl {
    /**
     * Cooperative safe point, called at message boundaries (and inside long compute loops). Returns
     * immediately while the Job is running; parks the calling Worker while the Job is paused / stepping;
     * throws a CancellationException while the Job is cancelling (unwinding the Worker so its resources
     * are released).
     */
    suspend fun checkpoint()


    /**
     * Runs a blocking IO [block] such that it stays visible to the Job's quiescence detection. Workers
     * MUST route blocking IO through this rather than offloading to an uncounted dispatcher (e.g. a bare
     * `withContext(Dispatchers.IO)`), which would read as false quiescence and spuriously pause the Job.
     */
    suspend fun <R> runBlockingIo(block: () -> R): R


    /**
     * Publishes a Worker's live progress (e.g. row counts, or a preview sample) to its own trace, keyed by
     * [location] (the Worker's own [ObjectLocation]), for the interactive UI to poll while the Job runs.
     * This is the always-on PUSH path — distinct from the on-demand request/reply a Worker may serve over a
     * duplex Channel. [value] is an arbitrary structured map (string keys; string / number / boolean / list
     * / nested-map values).
     *
     * Writes are throttled per [location] so a Worker emitting per-(small-)batch does not flood the trace;
     * pass [force] = true for the final value at end-of-stream so it is never dropped by the throttle.
     */
    fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean = false)
}
