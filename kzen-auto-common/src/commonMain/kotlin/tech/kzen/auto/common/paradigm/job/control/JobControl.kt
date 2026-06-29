package tech.kzen.auto.common.paradigm.job.control

import tech.kzen.auto.common.paradigm.job.api.JobLogicHost
import tech.kzen.lib.common.exec.logic.model.LogicPauseReason
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


    /**
     * The run-scoped [JobLogicHost] for invoking another Logic as a child (a Run Worker running a Script per
     * event). Most Workers never call this — it is the seam for nested-Logic Workers.
     */
    fun logicHost(): JobLogicHost


    /**
     * Request a Job-wide halt because a nested child Logic deliberately paused — a Pause step
     * ([LogicPauseReason.Explicit]) or a recoverable failure under pause-on-error ([LogicPauseReason.Error]),
     * i.e. it returned a paused result rather than finishing. Flips a free-running Job to pausing so every
     * Worker parks at its next [checkpoint] and the run driver reports the Job paused (to be inspected / fixed +
     * resumed) instead of running forward / deadlocking; while already pausing / stepping it just records the
     * halt reason so the wavefront settles as a halt rather than the loop's own boundary. Called by
     * nested-Logic Workers only, and ONLY for a deliberate halt — never for a plain [LogicPauseReason.Boundary]
     * step settle (which is the normal stepping mechanism).
     */
    fun requestHalt(reason: LogicPauseReason)
}
